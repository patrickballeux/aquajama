package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.sessions.Session;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AppleScriptTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "applescript";
    }

    @Override
    public ObjectNode getDefinition() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("script")
                .put("type", "string")
                .put("description", "The AppleScript source to execute.");
        parameters.putArray("required").add("script");

        ObjectNode function = MAPPER.createObjectNode();
        function.put("name", getName());
        function.put("description", "Execute AppleScript commands on macOS.");
        function.set("parameters", parameters);

        ObjectNode definition = MAPPER.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode action, Session session) throws Exception {

        String script = action.path("script").asText("");

        if (script.isBlank()) {
            return ToolResult.of("[applescript] Missing script");
        }
        System.out.println("osascript -e '%s'".formatted(script));
        ProcessBuilder pb = new ProcessBuilder(
                "/usr/bin/osascript",
                "-e",
                script
        );
        Process process = pb.start();

        BufferedReader out
                = new BufferedReader(new InputStreamReader(process.getInputStream()));

        BufferedReader err
                = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        StringBuilder result = new StringBuilder();
        String line;

        while ((line = out.readLine()) != null) {
            result.append(line).append("\n");
        }

        while ((line = err.readLine()) != null) {
            result.append(line).append("\n");
        }

        process.waitFor();

        boolean success = process.exitValue() == 0;

        String response = """
                          This is the successful result for the AppleScriptTool:

                          %s
                          
                          Rules:
                          - Answer the prompt of the user simply from this result.
                          - Do not invoke another tool.
                          
                        """.formatted(result.toString().trim().replace("\"", "\\\""));
        if (!success) {
            response = "The AppleScriptTool failed:  Here is the error message: %s".formatted(result.toString().trim().replace("\"", "\\\""));
        }
        System.out.println(response);
        return ToolResult.of(response);
    }
}
