package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.sessions.Session;

public class EchoTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "echo";
    }

    @Override
    public ObjectNode getDefinition() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("text")
                .put("type", "string")
                .put("description", "The text to echo back.");
        parameters.putArray("required").add("text");

        ObjectNode function = MAPPER.createObjectNode();
        function.put("name", getName());
        function.put("description", "Returns the provided text unchanged. Use only for testing tool integration.");
        function.set("parameters", parameters);

        ObjectNode definition = MAPPER.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode arguments, Session session) {

        String text = arguments.path("text").asText("");
        return ToolResult.of(text);
    }
}
