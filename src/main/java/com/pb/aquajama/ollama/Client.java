package com.pb.aquajama.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.aquajama.agent.tools.AgentTool;
import com.pb.aquajama.agent.tools.LaunchAppTool;
import com.pb.aquajama.agent.tools.ReadFileTool;
import com.pb.aquajama.agent.tools.ReadImageTool;
import com.pb.aquajama.agent.tools.SystemInfoTool;
import com.pb.aquajama.agent.tools.WebBrowserTool;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import javax.imageio.ImageIO;

/**
 * @author patrickballeux
 */
public class Client {

    public static final String LOCAL_URL = "http://localhost:11434";

    private static final java.util.logging.Logger logger
            = java.util.logging.Logger.getLogger(Client.class.getName());

    private final String url;
    private final List<AgentTool> tools = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public Client(String url) {
        this.url = url;
        this.initTools();
    }

    public List<AgentTool> getTools() {
        return tools;
    }

    private void initTools() {
        tools.add(new LaunchAppTool());
        tools.add(new ReadFileTool(mapper));
        tools.add(new ReadImageTool(mapper));
        tools.add(new SystemInfoTool());
        tools.add(new WebBrowserTool());
    }

    // ... getModels() etc. unchanged ...
    /**
     * Core streaming call to Ollama /api/generate.
     *
     * @param model model descriptor (includes canThink flag)
     * @param systemPrompt optional system prompt (may be null or blank)
     * @param userPrompt the actual prompt text (from user or a Tool)
     * @param images optional list of BufferedImage attachments (may be null or
     * empty)
     * @param listener streaming callback
     */
    public void sendPrompt(
            Model model,
            String systemPrompt,
            String userPrompt,
            List<BufferedImage> images,
            StreamListener listener
    ) {
        //System.out.println("*** " + userPrompt);
        // Client remains dumb: it only concatenates system + user.
        String prompt = buildPrompt(systemPrompt, userPrompt);

        // Normalize line breaks to keep JSON safe, minimal escaping.
        String normalizedPrompt = prompt
                .replace("\r", " ")
                .replace("\n", " ");

        // Build JSON body as text (no external libs except Jackson/ObjectMapper if you prefer)
        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append("{");
        bodyBuilder.append("\"model\": \"").append(escapeJson(model.name())).append("\",");
        bodyBuilder.append("\"prompt\": \"").append(escapeJson(normalizedPrompt)).append("\",");
        bodyBuilder.append("\"stream\": true,");
        bodyBuilder.append("\"think\": ").append(model.canThink());

        // Optional images
        List<String> encodedImages = encodeImages(images);
        if (!encodedImages.isEmpty()) {
            bodyBuilder.append(",\"images\":[");
            for (int i = 0; i < encodedImages.size(); i++) {
                if (i > 0) {
                    bodyBuilder.append(",");
                }
                bodyBuilder.append("\"").append(encodedImages.get(i)).append("\"");
            }
            bodyBuilder.append("]");
        }

        bodyBuilder.append("}");
        String body = bodyBuilder.toString();

        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/generate"))
                        .timeout(Duration.ofMinutes(2))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<java.io.InputStream> response
                        = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    listener.onComplete(new RuntimeException("HTTP " + response.statusCode()));
                    return;
                }

                try (var is = response.body(); var reader = new java.io.BufferedReader(new InputStreamReader(is))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }

                        JsonNode node;
                        try {
                            node = mapper.readTree(line);
                        } catch (JsonProcessingException parseEx) {
                            // Skip malformed JSON lines in the stream.
                            continue;
                        }

                        String text = node.path("response").asText("");
                        String thinkingText = node.path("thinking").asText("");
                        boolean done = node.path("done").asBoolean(false);

                        if (!thinkingText.isEmpty()) {
                            listener.onToken(new Token(thinkingText, true));
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
                    logger.log(Level.SEVERE, "Error reading response stream: {0}", ex.getMessage());
                    listener.onComplete(ex);
                }
            } catch (IOException | InterruptedException ex) {
                logger.log(Level.SEVERE, "Error sending prompt: {0}", ex.getMessage());
                listener.onComplete(ex);
            }
        }, "ollama-stream-" + System.currentTimeMillis()).start();
    }

    /**
     * Simple concatenation of system + user prompts. Tools and the app decide
     * the actual content and any tool protocol.
     */
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

    /**
     * Encode images to base64 PNG strings for the "images" field.
     */
    private List<String> encodeImages(List<BufferedImage> images) {
        List<String> result = new ArrayList<>();
        if (images == null) {
            return result;
        }
        for (BufferedImage img : images) {
            if (img == null) {
                continue;
            }
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(img, "png", baos);
                byte[] bytes = baos.toByteArray();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                result.add(base64);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to encode image: {0}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * Minimal JSON string escaper for quotes and backslashes.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' ->
                    sb.append("\\\\");
                case '"' ->
                    sb.append("\\\"");
                case '\b' ->
                    sb.append("\\b");
                case '\f' ->
                    sb.append("\\f");
                case '\n' ->
                    sb.append("\\n");
                case '\r' ->
                    sb.append("\\r");
                case '\t' ->
                    sb.append("\\t");
                default ->
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Model> getModels() {
        var models = new ArrayList<Model>();
        try {
            HttpRequest listReq = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> listResp
                    = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());

            if (listResp.statusCode() != 200) {
                return models;
            }

            JsonNode root = mapper.readTree(listResp.body());
            JsonNode modelNodes = root.path("models");
            if (!modelNodes.isArray()) {
                return models;
            }

            for (JsonNode m : modelNodes) {
                String name = m.path("name").asText();
                if (name == null || name.isBlank()) {
                    continue;
                }

                HttpRequest showReq = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/show"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                            { "model": "%s" }
                            """.formatted(name)))
                        .build();

                HttpResponse<String> showResp;
                try {
                    showResp = httpClient.send(showReq, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    logger.log(Level.WARNING,
                            "Failed to fetch details for {0}: {1}",
                            new Object[]{name, e.getMessage()});
                    models.add(new Model(name, false, false));
                    continue;
                }

                if (showResp.statusCode() != 200) {
                    models.add(new Model(name, false, false));
                    continue;
                }

                JsonNode showRoot = mapper.readTree(showResp.body());
                JsonNode modelfile = showRoot.path("modelfile");
                JsonNode details = showRoot.path("details");
                JsonNode modelInfo = showRoot.path("model_info");
                JsonNode parameters = showRoot.path("parameters");

                String modelfileText = modelfile.isTextual() ? modelfile.asText() : "";
                String lowerModelfile = modelfileText.toLowerCase();

                boolean canThink = false;
                boolean canUseVision = false;

                // --- THINKING detection ---
                if (lowerModelfile.contains("renderer ")
                        && lowerModelfile.contains("-thinking")) {
                    canThink = true;
                }
                if (lowerModelfile.contains("parser ")
                        && lowerModelfile.contains("-thinking")) {
                    canThink = true;
                }
                if (lowerModelfile.contains("parameter think true")
                        || lowerModelfile.contains("parameter think 1")) {
                    canThink = true;
                }

               

                // --- VISION detection (stricter) ---
                // 1) Check details.families for known vision families
                if (details != null) {
                    JsonNode familiesNode = details.path("families");
                    if (familiesNode.isArray()) {
                        for (JsonNode fam : familiesNode) {
                            String f = fam.asText("").toLowerCase();
                            if (f.contains("vision") || f.contains("llava") || f.contains("siglip")) {
                                canUseVision = true;
                                break;
                            }
                        }
                    }
                }

                // 2) Check FROM line for known vision base models
                if (!canUseVision && !modelfileText.isEmpty()) {
                    for (String line : modelfileText.split("\n")) {
                        String l = line.trim().toLowerCase();
                        if (!l.startsWith("from ")) {
                            continue;
                        }
                        if (l.contains("vision")
                                || l.contains("llava")
                                || l.contains("llama3.2-vision")
                                || l.contains("clip")
                                || l.contains("siglip")) {
                            canUseVision = true;
                            break;
                        }
                    }
                }

                // 3) Optionally, look into model_info/parameters for image / vision hints
                if (!canUseVision && modelInfo != null && modelInfo.isObject()) {
                    // very conservative: only react to explicit vision/image keys
                    var fieldIter = modelInfo.fields();
                    while (fieldIter.hasNext()) {
                        var entry = fieldIter.next();
                        String key = entry.getKey().toLowerCase();
                        if (key.contains("vision") || key.contains("image")) {
                            canUseVision = true;
                            break;
                        }
                    }
                }
                if (!canUseVision && parameters != null && parameters.isTextual()) {
                    String p = parameters.asText().toLowerCase();
                    if (p.contains("vision ") || p.contains("vision=true") || p.contains("vision 1")) {
                        canUseVision = true;
                    }
                }

                models.add(new Model(name, canUseVision, canThink));
            }

        } catch (IOException | InterruptedException e) {
            logger.log(Level.WARNING, "Failed to list models: {0}", e.getMessage());
        }

        return models;
    }

}
