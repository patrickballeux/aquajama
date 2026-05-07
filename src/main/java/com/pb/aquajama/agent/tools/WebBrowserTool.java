package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.sessions.Session;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URL;

public class WebBrowserTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "web_browser";
    }

    @Override
    public ObjectNode getDefinition() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("url")
                .put("type", "string")
                .put("description", "The HTTP or HTTPS URL to retrieve.");
        parameters.putArray("required").add("url");

        ObjectNode function = MAPPER.createObjectNode();
        function.put("name", getName());
        function.put("description", "Retrieve readable text from a webpage.");
        function.set("parameters", parameters);

        ObjectNode definition = MAPPER.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode node, Session session) {

        String url = node.path("url").asText();

        if (url.isBlank()) {
            return ToolResult.of("[web_browser] Missing URL");
        }

        try {
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

            return ToolResult.of(prompt);

        } catch (Exception e) {
            return ToolResult.of("[web_browser] Error: " + e.getMessage());
        }
    }
}
