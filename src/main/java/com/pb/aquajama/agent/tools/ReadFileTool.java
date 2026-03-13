package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.aquajama.ollama.Token;
import com.pb.aquajama.sessions.Session;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Tool to load a text file (local path or HTTP/HTTPS URL) and send its content
 * to the model for analysis.
 */
public class ReadFileTool implements AgentTool {

    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ReadFileTool(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String getActionName() {
        return "read_file";
    }

    public String getDescription() {
        return "Read the contents of a text file (local path or HTTP/HTTPS URL) and send them to the AI.";
    }

    @Override
    public String buildRuleSnippet() {
        return """
               read_file: Load a TEXT file and use its content in your answer.
               Use when the user asks to read, summarize, or analyze a text file.

               JSON command examples (inside ```json ```):

               { "action": "read_file",
                 "path": "/path/to/file.txt",
                 "max_chars": 8000 }

               { "action": "read_file",
                 "path": "https://example.com/some.txt",
                 "max_chars": 8000 }

               "path" is required (local path or HTTP/HTTPS URL).
               "max_chars" is optional (default 8000) and limits how many characters are sent.
               """;
    }

    @Override
    public boolean supports(JsonNode action) {
        return getActionName().equals(action.path("action").asText(""));
    }

    /**
     * New contract used by Session: the tool talks directly to Client via
     * Session.
     */
    @Override
    public void execute(JsonNode action, Session session) throws Exception {
        String pathStr = action.path("path").asText(null);
        if (pathStr == null || pathStr.isBlank()) {
            sendToUi(session, "[read_file] Missing 'path' parameter.\n");
            return;
        }

        int maxChars = action.path("max_chars").asInt(8000);
        if (maxChars <= 0) {
            maxChars = 8000;
        }

        String content = loadText(pathStr, maxChars);
        if (content == null || content.isEmpty()) {
            sendToUi(session, "[read_file] Unable to read text from: " + pathStr + "\n");
            return;
        }

        boolean truncated = content.length() >= maxChars;

        sendToUi(session, "[read_file] Loaded text from: " + pathStr
                + (truncated ? " (truncated)" : "") + "\n");

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
        """.formatted(session.getLastUserPrompt(), content);

        session.sendToolResult(userPrompt, List.of());        
    }

    // ------------------------------------------------------------------------
    private String loadText(String pathOrUrl, int maxChars) {
        try {
            String content;
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                content = loadFromUrl(pathOrUrl);
            } else {
                content = loadFromFile(pathOrUrl);
            }

            if (content == null) {
                return null;
            }
            if (content.length() > maxChars) {
                return content.substring(0, maxChars);
            }
            return content;
        } catch (Exception e) {
            return null;
        }
    }

    private String loadFromFile(String pathStr) throws IOException {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IOException("File not found or not regular: " + pathStr);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String loadFromUrl(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<byte[]> response
                = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }

        // Assume UTF-8 text; for more advanced use, inspect headers.
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private void sendToUi(Session session, String msg) {
        if (session.getUiConsumer() != null) {
            session.getUiConsumer().accept(new Token(msg, false));
        }
    }
}
