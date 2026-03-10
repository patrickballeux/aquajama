package com.pb.aquajama.ollama;

import java.awt.image.BufferedImage;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Client {

    public static final String LOCAL_URL = "http://localhost:11434";

    private final String url;
    private final HttpClient httpClient;

    private final OllamaModelService modelService;
    private final OllamaStreamHandler streamHandler;

    public Client(String url) {
        this.url = url;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.modelService = new OllamaModelService(url, httpClient);
        this.streamHandler = new OllamaStreamHandler(url, httpClient);
    }

    public List<Model> getModels() {
        return modelService.getModels();
    }

    public void sendPrompt(
            Model model,
            String systemPrompt,
            String userPrompt,
            List<BufferedImage> images,
            boolean stream,
            StreamListener listener
    ) {

        List<String> encodedImages = ImageEncoder.encode(images);

        String body = buildChatRequest(
                model,
                systemPrompt,
                userPrompt,
                encodedImages,
                stream
        );

        streamHandler.stream(body, listener);
    }

    private String buildChatRequest(
            Model model,
            String systemPrompt,
            String userPrompt,
            List<String> images,
            boolean stream
    ) {

        StringBuilder body = new StringBuilder();

        body.append("{");
        body.append("\"model\":\"").append(model.name()).append("\",");

        body.append("\"messages\":[");

        boolean first = true;

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.append("{");
            body.append("\"role\":\"system\",");
            body.append("\"content\":").append(jsonEscape(systemPrompt));
            body.append("}");
            first = false;
        }

        if (userPrompt != null) {

            if (!first) {
                body.append(",");
            }

            body.append("{");
            body.append("\"role\":\"user\",");
            body.append("\"content\":").append(jsonEscape(userPrompt));

            if (!images.isEmpty()) {
                body.append(",\"images\":[");
                for (int i = 0; i < images.size(); i++) {
                    if (i > 0) {
                        body.append(",");
                    }
                    body.append("\"").append(images.get(i)).append("\"");
                }
                body.append("]");
            }

            body.append("}");
        }

        body.append("],");
        
        body.append("\"stream\":").append(stream);

        body.append("}");

        return body.toString();
    }

    private String jsonEscape(String text) {
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }
}
