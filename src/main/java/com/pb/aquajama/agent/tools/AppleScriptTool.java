package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.pb.aquajama.ollama.Token;
import com.pb.aquajama.sessions.Session;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AppleScriptTool implements AgentTool {

    @Override
    public String getActionName() {
        return "applescript";
    }

    @Override
    public String buildRuleSnippet() {
        return """
Tool: applescript

Use this tool to execute AppleScript commands on macOS.

Example:
{
  "action": "applescript",
  "script": "tell application \\"Safari\\" to activate"
}

Use this tool when you need to control macOS applications.
Only output the JSON action when invoking the tool.
""";
    }

    @Override
    public boolean supports(JsonNode action) {
        return "applescript".equalsIgnoreCase(action.path("action").asText());
    }

    @Override
    public void execute(JsonNode action, Session session) throws Exception {

        String script = action.path("script").asText("");

        if (script.isBlank()) {
            session.getUiConsumer().accept(new Token("[applescript] Missing script\n",false,false));
            return;
        }

        ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);

        Process process = pb.start();

        BufferedReader out =
                new BufferedReader(new InputStreamReader(process.getInputStream()));

        BufferedReader err =
                new BufferedReader(new InputStreamReader(process.getErrorStream()));

        StringBuilder result = new StringBuilder();
        String line;

        while ((line = out.readLine()) != null) {
            result.append(line).append("\n");
        }

        while ((line = err.readLine()) != null) {
            result.append(line).append("\n");
        }

        process.waitFor();

        session.getUiConsumer().accept(new Token("[applescript]\n" + result + "\n",false,false));
    }
}