package com.pb.aquajama.sessions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.aquajama.agent.tools.AgentTool;
import com.pb.aquajama.ollama.Client;
import com.pb.aquajama.ollama.Model;
import com.pb.aquajama.ollama.StreamListener;
import com.pb.aquajama.ollama.Token;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class Session implements StreamListener {

    private String lastUserPrompt = "";
    public final Model model;
    private final Client client;
    private final ObjectMapper mapper = new ObjectMapper();
    
    private String lastMessage = "";

    private final List<AgentTool> tools;

    private Consumer<Token> uiConsumer;
    private String defaultSystemPrompt = "";

    public Session(Model model, Client client) {
        this.model = model;
        this.client = client;
        this.tools = client.getTools();
    }

    public String getToolSystemPrompt() {
        // Build tools description
        String toolRules = client.getTools().stream()
                .map(com.pb.aquajama.agent.tools.AgentTool::buildRuleSnippet)
                .collect(java.util.stream.Collectors.joining("\n\n"));

        // Global system prompt for the session
        String systemPrompt = """
        You are a local desktop assistant with access to these tools.

        Tools:
        %s

        When appropriate, you MUST use these tools by emitting a JSON block
        inside ```json ...``` with the correct "action" and parameters.
        After the tool is executed, you will receive the tool result and
        should respond to the user related to their query.
        Do not explain how you will use the tool.  Keep it simple.                      
        """.formatted(toolRules);

        return systemPrompt;
    }

    public void setUiConsumer(Consumer<Token> uiConsumer) {
        this.uiConsumer = uiConsumer;
    }

    public void setDefaultSystemPrompt(String systemPrompt) {
        this.defaultSystemPrompt = systemPrompt;
    }

    // --- Public API used by UI ----------------------------------------------
    public void sendUserPrompt(String userText) {
        sendPrompt(this.getDefaultSystemPrompt(), userText, Collections.emptyList());
    }

    public void sendUserPrompt(String userText, List<java.awt.image.BufferedImage> images) {
        sendPrompt(this.getDefaultSystemPrompt(), userText, images);
    }

    public void sendPrompt(String systemPrompt,
            String userPrompt,
            List<java.awt.image.BufferedImage> images) {
        this.lastUserPrompt = userPrompt;
        this.lastMessage = "";
        if (uiConsumer != null) {
            uiConsumer.accept(new Token("* %s\n".formatted(userPrompt), false));
        }
        client.sendPrompt(
                model,
                systemPrompt,
                userPrompt,
                images,
                this // Session remains the stream listener
        );
    }

    // --- StreamListener implementation --------------------------------------
    @Override
    public void onToken(Token token) {
        String text = token.text();
        lastMessage += text;
        if (uiConsumer != null && !text.isEmpty()) {
            uiConsumer.accept(token);
        }
        if (token.isThinking()) {
            return;
        }
        JsonNode toolNode = extractJsonToolBlock(lastMessage);
        if (toolNode != null) {
            lastMessage = "";
            handleToolInvocation(toolNode);
        }
    }

    @Override
    public void onComplete(Throwable error) {
        this.lastMessage = "";
        if (error == null) {
            if (uiConsumer != null) {
                uiConsumer.accept(new Token("\n", false));
            }
        } else {
            if (uiConsumer != null) {
                uiConsumer.accept(new Token("\n[Error] " + error.getMessage() + "\n", false));
            }
        }
    }

    // --- Tool detection & invocation ----------------------------------------
    private JsonNode extractJsonToolBlock(String content) {
        if (content == null) {
            return null;
        }

        // Find the last ```json fence
        int fenceStart = content.lastIndexOf("```json");
        if (fenceStart == -1) {
            return null;
        }

        // Find the closing ``` after that
        int fenceEnd = content.indexOf("```", fenceStart + 7);
        if (fenceEnd == -1) {
            // we have not yet received the full fenced block
            return null;
        }

        // Extract only the fenced block content
        int jsonStartRegion = fenceStart + 7; // after "```json"
        String fenced = content.substring(jsonStartRegion, fenceEnd).trim();

        // Now look for a full { ... } inside that fenced region
        int braceStart = fenced.indexOf('{');
        int braceEnd = fenced.lastIndexOf('}');
        if (braceStart == -1 || braceEnd == -1 || braceEnd <= braceStart) {
            return null;
        }

        String jsonStr = fenced.substring(braceStart, braceEnd + 1).trim();
        try {
            JsonNode node = mapper.readTree(jsonStr);
            if (node.has("action") || node.has("tool")) {
                return node;
            }
        } catch (JsonProcessingException e) {
            // ignore invalid JSON, wait for more tokens
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
                    uiConsumer.accept(new Token("\n[Action] Error in tool " + tool.getActionName() + ": " + e.getMessage() + "\n", false));
                }
                return;
            }
        }

        if (uiConsumer != null) {
            uiConsumer.accept(new Token("\n[Action] Unknown tool/action: " + type + "\n", false));
        }
    }

    // --- Accessors used by tools -------------------------------------------
    public Client getClient() {
        return client;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public String getDefaultSystemPrompt() {
        return defaultSystemPrompt.concat("\n").concat(getToolSystemPrompt());
    }

    public Consumer<Token> getUiConsumer() {
        return uiConsumer;
    }
    public String getLastUserPrompt(){
        return lastUserPrompt;
    }
}
