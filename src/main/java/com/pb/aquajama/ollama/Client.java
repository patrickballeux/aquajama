package com.pb.aquajama.ollama;

import com.pb.aquajama.sessions.Message;

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

    public void sendMessages(
            Model model,
            List<Message> history,
            List<BufferedImage> images,
            boolean stream,
            StreamListener listener
    ) {

        List<String> encodedImages = ImageEncoder.encode(images);

        String body = buildChatRequest(
                model,
                history,
                encodedImages,
                stream
        );

        streamHandler.stream(body, listener);
    }

    private String buildChatRequest(
            Model model,
            List<Message> history,
            List<String> images,
            boolean stream
    ) {

        StringBuilder body = new StringBuilder();

        body.append("{");
        body.append("\"model\":\"").append(model.name()).append("\",");

        body.append("\"messages\":[");

        boolean first = true;

        for (Message msg : history) {

            if (!first) {
                body.append(",");
            }

            body.append("{");
            body.append("\"role\":\"").append(msg.role()).append("\",");
            body.append("\"content\":").append(jsonEscape(msg.content()));

            if ("user".equals(msg.role()) && !images.isEmpty()) {

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

            first = false;
        }

        body.append("],");

        if (model.canThink()) {
            body.append("\"options\":{");
            body.append("\"think\":true");
            body.append("},");
        }

        body.append("\"stream\":").append(stream);

        body.append("}");

        return body.toString();
    }

    private String jsonEscape(String text) {

        if (text == null) {
            return "\"\"";
        }

        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                + "\"";
    }
}
