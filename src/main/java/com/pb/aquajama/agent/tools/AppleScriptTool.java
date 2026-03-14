package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.pb.aquajama.ollama.Token;
import com.pb.aquajama.sessions.Session;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class AppleScriptTool implements AgentTool {

    @Override
    public String getActionName() {
        return "applescript";
    }

    @Override
    public String buildRuleSnippet() {
        return """
Tool: applescript

Execute AppleScript commands on macOS.

Example:
{
  "action": "applescript",
  "script": "tell application \"Safari\" to activate"
}

Guidelines:
- Only output the JSON action when invoking the tool.
- The script must be valid AppleScript.
- Keep scripts simple whenever possible.
- Prefer AppleScript built-in commands before using `tell application`.

Common examples:
- Current date → `current date`
- Current time → `time string of (current date)`
- Open Safari → `tell application "Safari" to activate`

Error handling:
- If the tool returns an error, analyze the error message and retry with a corrected script.
- Retry up to 5 times maximum.
- Each retry should simplify the script rather than making it more complex.
- If the task cannot be completed after retries, explain the limitation to the user.              
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
            session.getUiConsumer().accept(new Token("[applescript] Missing script\n", false, false));
            return;
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
        session.sendToolResult(response, List.of());
    }
}
