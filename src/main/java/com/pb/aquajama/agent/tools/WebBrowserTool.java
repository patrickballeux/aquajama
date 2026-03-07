package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.pb.aquajama.ollama.Token;
import com.pb.aquajama.sessions.Session;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class WebBrowserTool implements AgentTool {

    @Override
    public String getActionName() {
        return "web_browser";
    }

    @Override
    public String getDescription() {
        return "Fetch the content of a web page (URL) using curl so the AI can summarize or extract information.";
    }

    @Override
    public String buildRuleSnippet() {
        return """
               web_browser: Fetch the content of a web page.

               Use this tool when the user asks you to:
               - open a specific URL
               - read / summarize a web page
               - extract information from a given link

               JSON command example (inside ```json ```):

               {
                 "action": "web_browser",
                 "url": "https://example.com/article"
               }

               You must always provide a full URL including protocol (http:// or https://).
               The tool will return the HTML/text content of the page, which you should
               then summarize or use to answer the user's question.
               """;
    }

    @Override
    public boolean supports(JsonNode action) {
        return "web_browser".equals(action.path("action").asText(""));
    }

    @Override
    public void execute(JsonNode action, Session session) throws Exception {
        String url = action.path("url").asText("").trim();
        if (url.isEmpty()) {
            sendToUi(session, "[web_browser] Missing 'url' parameter.\n");
            return;
        }

        // Very basic safety: only http/https
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            sendToUi(session, "[web_browser] URL must start with http:// or https://\n");
            return;
        }

        sendToUi(session, "[web_browser] Fetching URL: " + url + "\n");

        String content;
        try {
            content = fetchWithCurl(url);
        } catch (Exception e) {
            sendToUi(session, "[web_browser] Error while fetching URL: " + e.getMessage() + "\n");

            String userPromptError = """
                            I tried to open the following URL:

                            %s

                            but an error occurred in the local browser tool: %s

                            Please briefly explain to the user that the fetch failed.
                            - Do NOT call any tools again.
                            - Do NOT emit JSON.
                    """.formatted(url, e.getMessage());

            session.getClient().sendPrompt(
                    session.model,
                    session.getDefaultSystemPrompt(),
                    userPromptError,
                    java.util.Collections.emptyList(),
                    session
            );
            return;
        }

        if (content == null || content.isEmpty()) {
            sendToUi(session, "[web_browser] Empty content received from URL.\n");

            String userPromptEmpty = """
                    I opened the following URL:

                    %s

                    but there was no readable content.

                    Please briefly explain this to the user.
                    - Do NOT call any tools again.
                    - Do NOT emit JSON.
                    """.formatted(url);

            session.getClient().sendPrompt(
                    session.model,
                    session.getDefaultSystemPrompt(),
                    userPromptEmpty,
                    java.util.Collections.emptyList(),
                    session
            );
            return;
        }

        // Optionally truncate very large pages
        int maxChars = 16000;
        boolean truncated = false;
        if (content.length() > maxChars) {
            content = content.substring(0, maxChars);
            truncated = true;
        }

        String userPrompt = """
                I have fetched the content of the following web page:

                %s

                The raw HTML/text content is:

                %s

                %s
                IMPORTANT:
                - Do NOT call any tools again.
                - Do NOT emit JSON.
                - Summarize this page or answer the user's original question
                  based on this content, in clear natural language.
                """.formatted(
                url,
                content,
                truncated
                        ? "(Note: The content was truncated for length.)\n"
                        : ""
        );

        session.getClient().sendPrompt(
                session.model,
                session.getDefaultSystemPrompt(),
                userPrompt,
                java.util.Collections.emptyList(),
                session
        );
    }

    // ---------------------------------------------------------------------
    // "Browser" implementation using curl
    // ---------------------------------------------------------------------

    private String fetchWithCurl(String url) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("curl", "-L", "-s", url);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
        }

        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("curl exit code " + code);
        }

        return out.toString();
    }

    private void sendToUi(Session session, String msg) {
        if (session.getUiConsumer() != null) {
            session.getUiConsumer().accept(new Token(msg, false));
        }
    }
}
