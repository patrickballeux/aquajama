package com.pb.aquajama.ollama;

import java.util.List;

public class OllamaRequestBuilder {

    public static String buildGenerateRequest(
            Model model,
            String prompt,
            List<String> images,
            boolean stream
    ) {

        String normalizedPrompt = prompt
                .replace("\r", " ")
                .replace("\n", " ");

        StringBuilder body = new StringBuilder();

        body.append("{");
        body.append("\"model\":\"").append(model.name()).append("\",");
        body.append("\"prompt\":\"").append(escape(normalizedPrompt)).append("\",");
        body.append("\"stream\":%b,".formatted(stream));
        body.append("\"think\":").append(model.canThink());

        if (!images.isEmpty()) {

            body.append(",\"images\":[");

            for (int i = 0; i < images.size(); i++) {

                if (i > 0) body.append(",");

                body.append("\"").append(images.get(i)).append("\"");
            }

            body.append("]");
        }

        body.append("}");

        return body.toString();
    }

    private static String escape(String value) {

        if (value == null) return "";

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}