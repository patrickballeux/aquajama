package com.pb.aquajama.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
                models.add(inspectModel(name));
            }

        } catch (IOException | InterruptedException e) {

            logger.warning("Failed to list models: %s".formatted(e.getMessage()));
        }

        return models;
    }

    private Model inspectModel(String name) {

        boolean vision = false;
        boolean thinking = false;

        try {

            String body = """
        {
            "name": "%s"
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
                return new Model(name, vision, thinking);
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

                    if ("thinking".equalsIgnoreCase(c)) {
                        thinking = true;
                    }
                }
            }

        } catch (IOException | InterruptedException e) {

            logger.warning(() -> "Failed to inspect model " + name + ": " + e.getMessage());
        }

        return new Model(name, vision, thinking);
    }
}
