package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface AgentTool {

    String getName();

    ObjectNode getDefinition();

    ToolResult execute(JsonNode arguments, com.pb.aquajama.sessions.Session session) throws Exception;

    default boolean supports(String name) {
        return getName().equals(name);
    }
}
