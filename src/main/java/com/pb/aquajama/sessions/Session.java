package com.pb.aquajama.sessions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.aquajama.agent.tools.AgentTool;
import com.pb.aquajama.ollama.Client;
import com.pb.aquajama.ollama.Model;
import com.pb.aquajama.ollama.StreamListener;
import com.pb.aquajama.ollama.Token;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Session implements StreamListener {

    private final Model model;
    private final Client client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<AgentTool> tools;

    private final List<Message> history = new ArrayList<>();

    private Consumer<Token> uiConsumer;
    private String lastUserPrompt = "";
    private String assistantBuffer = "";
    private boolean possibleTool = false;

    public Session(Model model, Client client, List<AgentTool> tools) {
        this.model = Objects.requireNonNull(model);
        this.client = Objects.requireNonNull(client);
        this.tools = List.copyOf(Objects.requireNonNull(tools));
        history.clear();
        history.add(new Message("system", buildToolPrompt()));
    }

    public void setUiConsumer(Consumer<Token> uiConsumer) {
        this.uiConsumer = uiConsumer;
    }

    private String buildToolPrompt() {

        String toolRules = tools.stream()
                .map(AgentTool::buildRuleSnippet)
                .collect(Collectors.joining("\n\n"));

        return """              
        You are a local desktop assistant with access to these tools.

        Answer normally unless a tool is required.

        Tools:
        %s
       
        When using a tool return ONLY the JSON payload.
        When solving a problem:
                      - Start with the obvious answer first.
                      - Only use extended reasoning if the problem is complex.
                      - Keep reasoning concise.
        """.formatted(toolRules);
    }

    public void sendUserPrompt(String prompt) {

        lastUserPrompt = prompt;
        assistantBuffer = "";

        history.add(new Message("user", prompt));

        if (uiConsumer != null) {
            uiConsumer.accept(new Token("\n\n**" + prompt + "**\n\n", false, true));
        }

        client.sendMessages(
                model,
                history,
                Collections.EMPTY_LIST,
                true,
                this
        );
    }

    public void sendToolResult(String prompt, List<BufferedImage> images) {
        history.add(new Message("tool", prompt));
        client.sendMessages(model, history, images, true, this);
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

        if (t.contains("{")) {
            possibleTool = true;
        }

        JsonNode toolCall = extractTool(assistantBuffer);

        if (toolCall != null) {
            assistantBuffer = "";
            possibleTool = false;
            invokeTool(toolCall);
            return;
        }

        // Only display text if we're not parsing a tool
        if (!possibleTool && uiConsumer != null) {
            uiConsumer.accept(token);
        }
    }

    @Override
    public void onComplete(Throwable error) {
        if (error != null) {
            if (uiConsumer != null) {
                uiConsumer.accept(new Token("\n[Error] " + error.getMessage(), false, false));
            }
            return;
        }

        if (!assistantBuffer.isBlank()) {
            history.add(new Message("assistant", assistantBuffer));
            if (uiConsumer != null) {
                uiConsumer.accept(new Token("\n", false, false));
            }
        }
        assistantBuffer = "";
        possibleTool = false;
    }

    private JsonNode extractTool(String content) {

        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");

        if (start == -1 || end == -1) {
            return null;
        }

        try {
            JsonNode node = mapper.readTree(content.substring(start, end + 1));
            if (node.has("action") || node.has("tool")) {
                return node;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private void invokeTool(JsonNode node) {

        String action = node.path("action").asText("");

        for (AgentTool tool : tools) {
            try {

                if (tool.supports(node)) {
                    tool.execute(node, this);
                    return;
                }

            } catch (Exception e) {

                if (uiConsumer != null) {
                    uiConsumer.accept(new Token(
                            "[Tool error] " + e.getMessage(),
                            false, false
                    ));
                }

                return;
            }
        }

        if (uiConsumer != null) {
            uiConsumer.accept(new Token(
                    "[Unknown tool] " + action,
                    false, false
            ));
        }
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
