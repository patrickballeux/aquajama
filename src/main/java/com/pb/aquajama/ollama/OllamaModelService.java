package com.pb.aquajama.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class OllamaModelService {

    private static final Logger logger =
            Logger.getLogger(OllamaModelService.class.getName());

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

            HttpResponse<String> resp =
                    httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200)
                return models;

            JsonNode root = mapper.readTree(resp.body());

            JsonNode nodes = root.path("models");

            if (!nodes.isArray())
                return models;

            for (JsonNode m : nodes) {

                String name = m.path("name").asText();

                if (name == null || name.isBlank())
                    continue;

                models.add(new Model(name, false, false));
            }

        } catch (Exception e) {

            logger.warning("Failed to list models: " + e.getMessage());
        }

        return models;
    }
}