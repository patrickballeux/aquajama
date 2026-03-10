package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
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

        session.getClient().sendPrompt(session.getModel(), "Simply stream this to the user.", text, null, true, session);
        
    }

    @Override
    public String buildRuleSnippet() {
        return """
        Tool: echo
        Use this tool to return text to the user.
        Example:
        {
          "action": "echo",
          "text": "Hello"
        }
        """;
    }
}