package com.pb.aquajama.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OllamaStreamHandler {

    private static final Logger logger =
            Logger.getLogger(OllamaStreamHandler.class.getName());

    private final String url;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaStreamHandler(String url, HttpClient httpClient) {
        this.url = url;
        this.httpClient = httpClient;
    }

    public void stream(String body, StreamListener listener) {

        new Thread(() -> {

            try {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/chat"))
                        .timeout(Duration.ofMinutes(2))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<java.io.InputStream> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    listener.onComplete(new RuntimeException("HTTP " + response.statusCode()));
                    return;
                }

                try (var is = response.body();
                     var reader = new BufferedReader(new InputStreamReader(is))) {

                    String line;

                    while ((line = reader.readLine()) != null) {

                        line = line.trim();

                        if (line.isEmpty()) {
                            continue;
                        }

                        JsonNode node;

                        try {
                            node = mapper.readTree(line);
                        } catch (Exception e) {
                            continue;
                        }

                        String text = "";
                        String thinking = "";
                        boolean done = node.path("done").asBoolean(false);

                        // -------- CHAT ENDPOINT --------
                        if (node.has("message")) {

                            JsonNode message = node.get("message");

                            if (message.has("content")) {
                                text = message.get("content").asText("");
                            }
                        }

                        // -------- GENERATE ENDPOINT --------
                        else if (node.has("response")) {
                            text = node.get("response").asText("");
                        }

                        // optional thinking field
                        if (node.has("thinking")) {
                            thinking = node.get("thinking").asText("");
                        }

                        if (!thinking.isEmpty()) {
                            listener.onToken(new Token(thinking, true));
                        }

                        if (!text.isEmpty()) {
                            listener.onToken(new Token(text, false));
                        }

                        if (done) {
                            break;
                        }
                    }

                    listener.onComplete(null);

                } catch (IOException ex) {

                    logger.log(Level.SEVERE,
                            "Error reading stream: {0}", ex.getMessage());

                    listener.onComplete(ex);
                }

            } catch (IOException | InterruptedException ex) {

                logger.log(Level.SEVERE,
                        "Error sending request: {0}", ex.getMessage());

                listener.onComplete(ex);
            }

        }, "ollama-stream").start();
    }
}