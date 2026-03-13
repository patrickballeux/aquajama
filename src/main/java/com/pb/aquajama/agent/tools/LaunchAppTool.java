package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.pb.aquajama.ollama.Token;
import com.pb.aquajama.sessions.Session;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool to list and launch macOS applications from /Applications and
 * ~/Applications.
 */
public class LaunchAppTool implements AgentTool {

    @Override
    public String getActionName() {
        // Informational; we actually support "launch_app" and "list_apps".
        return "launch_app";
    }

    public String getDescription() {
        return "List and launch macOS applications from /Applications and ~/Applications.";
    }

    @Override
    public String buildRuleSnippet() {
        return """
               launch_app / list_apps: Manage macOS applications.

               To list installed applications:
               { "action": "list_apps" }

               To launch an application:
               { "action": "launch_app", "name": "Safari" }

               "name" should match the application name as seen in Finder (without .app).
               """;
    }

    @Override
    public boolean supports(JsonNode action) {
        String type = action.path("action").asText("");
        return "launch_app".equals(type) || "list_apps".equals(type);
    }

    /**
     * New contract: Session calls this for tools that manage their own
     * conversation.
     */
    @Override
    public void execute(JsonNode action, Session session) throws Exception {
        String type = action.path("action").asText("");

        if ("list_apps".equals(type)) {
            String list = listApplications();
            //sendToUi(session, "[launch_app] Listed installed applications.\n");

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
        """.formatted(session.getLastUserPrompt(), list);

            session.sendToolResult(userPrompt, List.of());

        } else if ("launch_app".equals(type)) {
            String name = action.path("name").asText("");
            if (name.isBlank()) {
                sendToUi(session, "[launch_app] Missing 'name' parameter.\n");
                return;
            }

            String result = launchApplicationByName(name);
            sendToUi(session, "[launch_app] " + result + "\n");

            String userPrompt = """
                    I attempted to launch the macOS application named "%s".

                    Result:
                    %s

                    Please briefly confirm this action to the user and, if needed,
                    provide any relevant follow-up information.
                    """.formatted(name, result);

            session.sendToolResult(userPrompt, List.of());

        } else {
            sendToUi(session, "[launch_app] Unsupported action: " + type + "\n");
        }
    }

    // ---------------------------------------------------------------------
    // Implementation details
    // ---------------------------------------------------------------------
    private String listApplications() {
        List<String> apps = new ArrayList<>();

        File systemApps = new File("/Applications");
        File userApps = new File(System.getProperty("user.home"), "Applications");

        collectAppsFromFolder(systemApps, apps);
        collectAppsFromFolder(userApps, apps);

        if (apps.isEmpty()) {
            return "No .app bundles found in /Applications or ~/Applications.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Installed applications:\n");
        for (String app : apps) {
            sb.append("- ").append(app).append("\n");
        }
        return sb.toString();
    }

    private void collectAppsFromFolder(File folder, List<String> out) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File f : files) {
            if (f.getName().endsWith(".app")) {
                out.add(f.getName().substring(0, f.getName().length() - 4)); // drop .app
            }
        }
    }

    private String launchApplicationByName(String name) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("open", "-a", name);
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
            return "Failed to launch \"" + name + "\" (exit " + code + "):\n" + out;
        }

        return "Launched application: " + name;
    }

    private void sendToUi(Session session, String msg) {
        if (session.getUiConsumer() != null) {
            session.getUiConsumer().accept(new Token(msg, false));
        }
    }
}
