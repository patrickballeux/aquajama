package com.pb.aquajama.sessions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.agent.tools.AgentTool;
import com.pb.aquajama.agent.tools.ToolResult;
import com.pb.aquajama.ollama.Client;
import com.pb.aquajama.ollama.Model;
import com.pb.aquajama.ollama.StreamListener;
import com.pb.aquajama.ollama.ToolCall;
import com.pb.aquajama.ollama.Token;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;

public class Session implements StreamListener {

    private final Model model;
    private final Client client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<AgentTool> tools;

    private final List<Message> history = new ArrayList<>();

    private Consumer<Token> uiConsumer;
    private String lastUserPrompt = "";
    private String assistantBuffer = "";
    private final List<ToolCall> pendingToolCalls = new ArrayList<>();

    public Session(Model model, Client client, List<AgentTool> tools) {
        this.model = Objects.requireNonNull(model);
        this.client = Objects.requireNonNull(client);
        this.tools = List.copyOf(Objects.requireNonNull(tools));
        history.clear();
        history.add(new Message("system", buildSystemPrompt()));
    }

    public void setUiConsumer(Consumer<Token> uiConsumer) {
        this.uiConsumer = uiConsumer;
    }

    private String buildSystemPrompt() {
        if (!model.canUseTools() || tools.isEmpty()) {
            return """
            You are a local desktop assistant.

            Answer the user naturally.
            Keep reasoning concise.
            """;
        }

        return """
        You are a local desktop assistant.

        Answer normally unless a tool is required.
        Use the provided tools only when they are useful for the user's request.
        When a tool returns an observation, use it to answer the user naturally.
        Keep reasoning concise.
        """;
    }

    public void sendUserPrompt(String prompt) {

        lastUserPrompt = prompt;
        assistantBuffer = "";
        pendingToolCalls.clear();

        history.add(new Message("user", prompt));

        if (uiConsumer != null) {
            uiConsumer.accept(new Token("\n\n**" + prompt + "**\n\n", false, true));
        }

        client.sendMessages(
                model,
                history,
                tools,
                true,
                this
        );
    }

    public void sendToolResult(String prompt, List<BufferedImage> images) {
        history.add(Message.userWithImages(prompt, images));
        client.sendMessages(model, history, tools, true, this);
    }

    @Override
    public void onToken(Token token) {

        if (token.isThinking()) {
            if (uiConsumer != null) {
                uiConsumer.accept(token);
            }
            return;
        }

        String t = token.text();

        assistantBuffer += t;
        if (uiConsumer != null) {
            uiConsumer.accept(token);
        }
    }

    @Override
    public void onToolCall(ToolCall toolCall) {
        pendingToolCalls.add(toolCall);
    }

    @Override
    public void onComplete(Throwable error) {
        if (error != null) {
            if (uiConsumer != null) {
                uiConsumer.accept(new Token("\n[Error] " + error.getMessage(), false, false));
            }
            return;
        }

        if (!pendingToolCalls.isEmpty()) {
            List<ToolCall> toolCalls = List.copyOf(pendingToolCalls);
            pendingToolCalls.clear();
            processToolCalls(toolCalls);
            return;
        }

        if (!assistantBuffer.isBlank()) {
            history.add(new Message("assistant", assistantBuffer));
            if (uiConsumer != null) {
                uiConsumer.accept(new Token("\n", false, false));
            }
        }
        assistantBuffer = "";
    }

    private void processToolCalls(List<ToolCall> toolCalls) {
        history.add(Message.assistantToolCall(assistantBuffer, toOllamaToolCallNodes(toolCalls)));
        assistantBuffer = "";

        for (ToolCall toolCall : toolCalls) {
            ToolResult result = invokeTool(toolCall);
            history.add(Message.toolResult(toolCall.name(), result.content()));
        }

        client.sendMessages(model, history, tools, true, this);
    }

    private List<JsonNode> toOllamaToolCallNodes(List<ToolCall> toolCalls) {
        List<JsonNode> nodes = new ArrayList<>();

        for (ToolCall toolCall : toolCalls) {
            if (toolCall.raw() != null && !toolCall.raw().isMissingNode()) {
                nodes.add(toolCall.raw());
                continue;
            }

            ObjectNode function = mapper.createObjectNode();
            function.put("name", toolCall.name());
            function.set("arguments", toolCall.arguments());

            ObjectNode node = mapper.createObjectNode();
            node.put("id", toolCall.id());
            node.put("type", "function");
            node.set("function", function);
            nodes.add(node);
        }

        return nodes;
    }

    private ToolResult invokeTool(ToolCall toolCall) {
        for (AgentTool tool : tools) {
            if (!tool.supports(toolCall.name())) {
                continue;
            }

            try {
                return tool.execute(toolCall.arguments(), this);
            } catch (Exception e) {
                return ToolResult.of("[Tool error] " + e.getMessage());
            }
        }

        return ToolResult.of("[Unknown tool] " + toolCall.name());
    }

    public Client getClient() {
        return client;
    }

    public Model getModel() {
        return model;
    }

    public String getLastUserPrompt() {
        return lastUserPrompt;
    }

    public Consumer<Token> getUiConsumer() {
        return uiConsumer;
    }
}
