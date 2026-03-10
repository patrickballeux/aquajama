package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.pb.aquajama.ollama.Token;
import com.pb.aquajama.sessions.Session;

public class EchoTool implements AgentTool {

    @Override
    public String getActionName() {
        return "echo";
    }

    @Override
    public boolean supports(JsonNode node) {
        return "echo".equalsIgnoreCase(node.path("action").asText());
    }

    @Override
    public void execute(JsonNode node, Session session) {

        String text = node.path("text").asText("");

        if (session.getUiConsumer() != null){
            session.getUiConsumer().accept(new Token(text,false));
        }        
    }

    @Override
    public String buildRuleSnippet() {
        return """
        Tool: echo
        DO NOT USE THIS TOOL.  Just use plain text.
        """;
    }
}