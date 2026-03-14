package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.pb.aquajama.ollama.Token;
import com.pb.aquajama.sessions.Session;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FileBrowserTool implements AgentTool {

    private File currentDirectory;

    public FileBrowserTool() {
        this.currentDirectory = new File(System.getProperty("user.home"));
    }

    @Override
    public String getActionName() {
        return "file_browser";
    }


    @Override
    public String buildRuleSnippet() {

        String home = System.getProperty("user.home");

        return """
        file_browser: Navigate and read files on the computer.

        The current directory starts at the user's home folder:
        %s

        Actions:

        List files:
        { "action": "file_browser", "command": "list" }

        Change directory:
        { "action": "file_browser", "command": "cd", "path": "Documents" }

        Read a file:
        { "action": "file_browser", "command": "read", "path": "notes.txt" }

        Rules:
        - Use 'list' before navigating.
        - Paths can be relative to the current directory.
        - Do not attempt to access system files.
        """.formatted(home);
    }

    @Override
    public boolean supports(JsonNode action) {
        return "file_browser".equals(action.path("action").asText());
    }

    @Override
    public void execute(JsonNode action, Session session) throws Exception {

        String command = action.path("command").asText("");

        switch (command) {

            case "list" ->
                listFiles(session);

            case "cd" ->
                changeDirectory(action.path("path").asText(), session);

            case "read" ->
                readFile(action.path("path").asText(), session);

            default ->
                send(session, "[file_browser] Unknown command\n");
        }
    }

    // --------------------------------------------------------
    private void listFiles(Session session) {

        File[] files = currentDirectory.listFiles();

        if (files == null) {
            send(session, "[file_browser] Unable to read directory\n");
            return;
        }

        String result = Arrays.stream(files)
                .map(f -> (f.isDirectory() ? "[DIR] " : "[FILE] ") + f.getName())
                .sorted()
                .collect(Collectors.joining("\n"));

        send(session, """
        Current directory:
        %s

        Contents:
        %s
        """.formatted(currentDirectory.getAbsolutePath(), result));
    }

    private void changeDirectory(String path, Session session) {

        File newDir = new File(currentDirectory, path);

        if (!newDir.exists() || !newDir.isDirectory()) {
            send(session, "[file_browser] Directory not found: " + path + "\n");
            return;
        }

        currentDirectory = newDir;

        send(session, "[file_browser] Now in: " + currentDirectory.getAbsolutePath() + "\n");
    }

    private void readFile(String path, Session session) throws Exception {

        File file = new File(currentDirectory, path);

        if (!file.exists() || !file.isFile()) {
            send(session, "[file_browser] File not found: " + path + "\n");
            return;
        }

        String content = Files.readString(file.toPath());

        if (content.length() > 4000) {
            content = content.substring(0, 4000) + "\n... (truncated)";
        }

        send(session, """
        File: %s

        %s
        """.formatted(file.getName(), content));
    }

    private void send(Session session, String msg) {

        if (session.getUiConsumer() != null) {
            session.getUiConsumer().accept(new Token(msg, false,false));
        }
    }
}
