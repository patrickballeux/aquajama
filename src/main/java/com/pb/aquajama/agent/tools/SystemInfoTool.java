package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.pb.aquajama.ollama.Token;
import com.pb.aquajama.sessions.Session;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SystemInfoTool implements AgentTool {

    @Override
    public String getActionName() {
        return "system_info";
    }

    @Override
    public String getDescription() {
        return "Query basic system information (disk, memory, processes).";
    }

    @Override
    public String buildRuleSnippet() {
        return """
               system_info: Query local system information.

               Use this tool when the user asks about:
               - free disk space
               - memory / RAM usage
               - running processes

               JSON command examples (inside ```json ```):

               { "action": "system_info", "query": "disk" }
               { "action": "system_info", "query": "memory" }
               { "action": "system_info", "query": "processes" }

               "query" can be "disk", "memory", or "processes".
               """;
    }

    @Override
    public boolean supports(JsonNode action) {
        return getActionName().equals(action.path("action").asText(""));
    }

    // Mac/Linux-style implementations; adapte si tu veux supporter Windows
    private String getDiskUsage() throws Exception {
        return runCommand("df", "-h");
    }

    private String getMemoryUsage() throws Exception {
        // macOS: vm_stat, Linux: free -h ; ici on fait simple pour macOS
        String vmStat = runCommand("vm_stat");
        return "vm_stat output:\n" + vmStat;
    }

    private String getProcesses() throws Exception {
        // Limiter la taille (ex.: top 25 lignes)
        String ps = runCommand("ps", "aux");
        String[] lines = ps.split("\\R");
        StringBuilder sb = new StringBuilder();
        int max = Math.min(lines.length, 25);
        for (int i = 0; i < max; i++) {
            sb.append(lines[i]).append("\n");
        }
        if (lines.length > max) {
            sb.append("... (").append(lines.length - max).append(" more lines)\n");
        }
        return sb.toString();
    }

    private String runCommand(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
        }

        int code = p.waitFor();
        if (code != 0) {
            return "Command failed (" + String.join(" ", cmd)
                    + ") with exit code " + code + "\n" + out;
        }

        return out.toString();
    }

    @Override
    public void execute(JsonNode action, Session session) throws Exception {
        String query = action.path("query").asText("").toLowerCase();

        String rawInfo;
        switch (query) {
            case "disk" ->
                rawInfo = getDiskUsage();
            case "memory" ->
                rawInfo = getMemoryUsage();
            case "processes" ->
                rawInfo = getProcesses();
            default -> {
                sendToUi(session,
                        "[system_info] Unsupported or missing 'query'. "
                        + "Use 'disk', 'memory', or 'processes'.\n");
                return;
            }
        }

        String userPrompt = """
        You previously requested to use a tool to answer the user's question:

        "%s"

        The tool has now finished and returned this RESULT:

        %s

        IMPORTANT:
        - Do NOT call any tools again.
        - Do NOT emit any JSON or tool actions.
        - Just answer the user's original question in natural language,
          using ONLY the RESULT above as context.
        """.formatted(session.getLastUserPrompt(), rawInfo);

        session.getClient().sendPrompt(
                session.model,
                session.getDefaultSystemPrompt(),
                userPrompt,
                java.util.Collections.emptyList(),
                session
        );
    }

    private void sendToUi(Session session, String msg) {
        if (session.getUiConsumer() != null) {
            session.getUiConsumer().accept(new Token(msg, false));
        }
    }
}
