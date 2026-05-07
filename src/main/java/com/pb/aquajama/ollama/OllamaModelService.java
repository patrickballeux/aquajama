package com.pb.aquajama.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class OllamaModelService {

    private static final Logger logger
            = Logger.getLogger(OllamaModelService.class.getName());

    private final String url;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaModelService(String url, HttpClient httpClient) {
        this.url = url;
        this.httpClient = httpClient;
    }

    public List<Model> getModels() {

        var models = new ArrayList<Model>();

        try {

            HttpRequest listReq = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> resp
                    = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                return models;
            }

            JsonNode root = mapper.readTree(resp.body());

            JsonNode nodes = root.path("models");

            if (!nodes.isArray()) {
                return models;
            }

            for (JsonNode m : nodes) {

                String name = m.path("name").asText();

                if (name == null || name.isBlank()) {
                    continue;
                }
                long sizeBytes = m.path("size").asLong(0);
                models.add(inspectModel(name, sizeBytes));
            }

        } catch (IOException | InterruptedException e) {

            logger.warning("Failed to list models: %s".formatted(e.getMessage()));
        }

        return models;
    }

    public Optional<Model> findSmallestModelWith(Model.Capability capability) {
        return getModels().stream()
                .filter(model -> model.supports(capability))
                .min(Comparator.comparingLong(model -> model.sizeBytes() <= 0 ? Long.MAX_VALUE : model.sizeBytes()));
    }

    private Model inspectModel(String name, long sizeBytes) {

        boolean vision = false;
        boolean tools = false;
        boolean thinking = false;

        try {

            String body = """
        {
            "model": "%s"
        }
        """.formatted(name);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/show"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp
                    = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                logger.warning("Model capabilities did not work %s %s".formatted(name, resp.statusCode()));
                return new Model(name, sizeBytes, vision, tools, thinking);
            }

            //System.out.println(resp.body());
            JsonNode root = mapper.readTree(resp.body());

            JsonNode capabilities = root.path("capabilities");

            if (capabilities.isArray()) {

                for (JsonNode cap : capabilities) {

                    String c = cap.asText();

                    if ("vision".equalsIgnoreCase(c)) {
                        vision = true;
                    }

                    if ("tools".equalsIgnoreCase(c) || "tool".equalsIgnoreCase(c)) {
                        tools = true;
                    }

                    if ("thinking".equalsIgnoreCase(c)) {
                        thinking = true;
                    }
                }
            }

            JsonNode details = root.path("details");
            if (!thinking) {
                String family = details.path("family").asText("");
                thinking = isKnownThinkingFamily(family) || isKnownThinkingFamily(name);
            }

        } catch (IOException | InterruptedException e) {

            logger.warning(() -> "Failed to inspect model " + name + ": " + e.getMessage());
        }

        return new Model(name, sizeBytes, vision, tools, thinking);
    }

    private boolean isKnownThinkingFamily(String value) {
        String normalized = value.toLowerCase();
        return normalized.contains("deepseek-r1")
                || normalized.contains("deepseek-v3.1")
                || normalized.contains("qwen3")
                || normalized.contains("gpt-oss");
    }
}
