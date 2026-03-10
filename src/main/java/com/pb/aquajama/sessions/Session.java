package com.pb.aquajama.sessions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.aquajama.agent.tools.AgentTool;
import com.pb.aquajama.ollama.Client;
import com.pb.aquajama.ollama.Model;
import com.pb.aquajama.ollama.StreamListener;
import com.pb.aquajama.ollama.Token;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Session implements StreamListener {

    private String lastUserPrompt = "";
    private String lastMessage = "";

    public final Model model;
    private final Client client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<AgentTool> tools;

    private Consumer<Token> uiConsumer;
    private String defaultSystemPrompt = "";

    public Session(Model model, Client client, List<AgentTool> tools) {
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tools = List.copyOf(Objects.requireNonNull(tools, "tools must not be null"));
    }

    public String getToolSystemPrompt() {
        String toolRules = tools.stream()
                .map(AgentTool::buildRuleSnippet)
                .collect(Collectors.joining("\n\n"));

        return """
                You are a local desktop assistant with access to these tools.
                Answer to the user using normal streaming text.
                Do not use the echo tool.

                Tools:
                %s

                When using a tool, just send the payload.
                Do not explain how you will use the tool. Keep it simple.
                """.formatted(toolRules);
    }

    public void setUiConsumer(Consumer<Token> uiConsumer) {
        this.uiConsumer = uiConsumer;
    }

    public void setDefaultSystemPrompt(String systemPrompt) {
        this.defaultSystemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    public void sendUserPrompt(String userText) {
        sendPrompt(getDefaultSystemPrompt(), userText, Collections.emptyList());
    }

    public void sendUserPrompt(String userText, List<BufferedImage> images) {
        sendPrompt(getDefaultSystemPrompt(), userText, images);
    }

    public void sendPrompt(String systemPrompt, String userPrompt, List<BufferedImage> images) {
        this.lastUserPrompt = userPrompt == null ? "" : userPrompt;
        this.lastMessage = "";

        if (uiConsumer != null && userPrompt != null && !userPrompt.isEmpty()) {
            uiConsumer.accept(new Token("* %s\n".formatted(userPrompt), false));
        }

        client.sendPrompt(
                model,
                systemPrompt,
                userPrompt,
                images,
                true,
                this
        );
    }

    @Override
    public void onToken(Token token) {

        String text = token.text();
        lastMessage += text;

        if (token.isThinking()) {
            if (uiConsumer != null) {
                uiConsumer.accept(token);
            }
            return;
        }

        JsonNode toolNode = extractJsonToolBlock(lastMessage);

        if (toolNode != null) {
            lastMessage = "";
            handleToolInvocation(toolNode);
            return;
        }
        // Wait until we know it's not a tool
        if (lastMessage.startsWith("{")) {
            return;
        }
        if (uiConsumer != null) {
            // sending from the start...
 
            uiConsumer.accept(token);
        }
    }

    @Override
    public void onComplete(Throwable error) {
        lastMessage = "";
        if (uiConsumer == null) {
            return;
        }

        if (error == null) {
            uiConsumer.accept(new Token("\n", false));
        } else {
            uiConsumer.accept(new Token("\n[Error] " + error.getMessage() + "\n", false));
        }
    }

    private JsonNode extractJsonToolBlock(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        int braceStart = content.indexOf('{');
        int braceEnd = content.lastIndexOf('}');
        if (braceStart == -1 || braceEnd == -1 || braceEnd <= braceStart) {
            return null;
        }

        String jsonStr = content.substring(braceStart, braceEnd + 1).trim();

        try {
            JsonNode node = mapper.readTree(jsonStr);
            if (node.has("action") || node.has("tool")) {
                return node;
            }
        } catch (JsonProcessingException e) {
            // wait for more tokens / ignore malformed partial JSON
        }

        return null;
    }

    private void handleToolInvocation(JsonNode node) {
        String type = node.path("action").asText("");

        for (AgentTool tool : tools) {
            try {
                if (tool.supports(node)) {
                    System.out.println("Invoking tool " + tool.getActionName());
                    tool.execute(node, this);
                    return;
                }
            } catch (Exception e) {
                if (uiConsumer != null) {
                    uiConsumer.accept(new Token(
                            "\n[Action] Error in tool " + tool.getActionName() + ": " + e.getMessage() + "\n",
                            false
                    ));
                }
                return;
            }
        }

        if (uiConsumer != null) {
            uiConsumer.accept(new Token("\n[Action] Unknown tool/action: " + type + "\n", false));
        }
    }

    public Client getClient() {
        return client;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public String getDefaultSystemPrompt() {
        if (defaultSystemPrompt == null || defaultSystemPrompt.isBlank()) {
            return getToolSystemPrompt();
        }
        return defaultSystemPrompt + "\n" + getToolSystemPrompt();
    }

    public Consumer<Token> getUiConsumer() {
        return uiConsumer;
    }

    public String getLastUserPrompt() {
        return lastUserPrompt;
    }

    public List<AgentTool> getTools() {
        return tools;
    }

    public Model getModel() {
        return model;
    }

    private boolean looksLikeToolProtocol(String text) {
        return text.contains("```json")
                || text.contains("\"action\"")
                || text.contains("\"tool\"");
    }
}
