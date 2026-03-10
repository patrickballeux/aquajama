package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.pb.aquajama.ollama.Model;
import com.pb.aquajama.ollama.StreamListener;
import com.pb.aquajama.sessions.Session;
import com.pb.aquajama.ollama.Token;
import java.awt.image.BufferedImage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URL;
import java.util.List;

public class WebBrowserTool implements AgentTool {

    @Override
    public String getActionName() {
        return "web_browser";
    }

    @Override
    public boolean supports(JsonNode node) {
        return "web_browser".equalsIgnoreCase(node.path("action").asText());
    }

    @Override
    public void execute(JsonNode node, Session session) {

        String url = node.path("url").asText();

        if (url.isBlank()) {
            session.getUiConsumer().accept(
                    new Token("[web_browser] Missing URL\n", false)
            );
            return;
        }

        try {

            session.getUiConsumer().accept(
                    new Token("🌐 Fetching webpage...\n", false)
            );

            Document doc = Jsoup.parse(new URL(url), 10000);

            String text = doc.text();

            // limiter la taille
            if (text.length() > 15000) {
                text = text.substring(0, 15000);
            }

            String prompt = """
            You are analyzing the following webpage content.

            Webpage:
            %s

            Extract the relevant information to answer the user's request.
            """.formatted(text);

            session.getClient().sendPrompt(
                session.getModel(),
                session.getDefaultSystemPrompt(),
                prompt,
                null,
                true,
                session
            );

        } catch (Exception e) {

            session.getUiConsumer().accept(
                    new Token("[web_browser] Error: " + e.getMessage() + "\n", false)
            );
        }
    }

    @Override
    public String buildRuleSnippet() {
        return """
        Tool: web_browser
        Use this tool to retrieve the content of a webpage.

        Example:
        {
          "action": "web_browser",
          "url": "https://example.com"
        }
        """;
    }
}
