package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {

    String getActionName(); // e.g. "send_email"

    String buildRuleSnippet(); // text to inject into system prompt

    boolean supports(JsonNode action);

    void execute(JsonNode action, com.pb.aquajama.sessions.Session session) throws Exception;
}
