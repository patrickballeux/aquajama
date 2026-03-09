package com.pb.aquajama.ollama;

import java.awt.image.BufferedImage;
import java.net.http.HttpClient;
import java.time.Duration;
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
            StreamListener listener
    ) {

        String prompt = buildPrompt(systemPrompt, userPrompt);

        List<String> encodedImages = ImageEncoder.encode(images);

        String body = OllamaRequestBuilder.buildGenerateRequest(
                model,
                prompt,
                encodedImages
        );

        streamHandler.stream(body, listener);
    }

    private String buildPrompt(String systemPrompt, String userPrompt) {

        StringBuilder sb = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append(systemPrompt.trim()).append("\n\n");
        }

        if (userPrompt != null) {
            sb.append(userPrompt);
        }

        return sb.toString();
    }
}