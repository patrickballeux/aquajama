package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.sessions.Session;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public class ReadImageTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "read_image";
    }

    @Override
    public boolean requiresVision() {
        return true;
    }

    @Override
    public ObjectNode getDefinition() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("path")
                .put("type", "string")
                .put("description", "A local image file path or HTTP/HTTPS image URL.");

        parameters.putArray("required").add("path");

        ObjectNode function = MAPPER.createObjectNode();
        function.put("name", getName());
        function.put("description", """
                Load an image from a local file path or URL so the vision-capable model can analyze it.
                """);
        function.set("parameters", parameters);

        ObjectNode definition = MAPPER.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode arguments, Session session) throws Exception {
        String path = arguments.path("path").asText("");

        if (path.isBlank()) {
            return ToolResult.of("[read_image] Missing path");
        }

        BufferedImage image;
        try {
            image = loadImage(path);
        } catch (IOException e) {
            return ToolResult.of("[read_image] Unable to load image: " + e.getMessage());
        }

        if (image == null) {
            return ToolResult.of("[read_image] Unsupported or unreadable image: " + path);
        }

        String prompt = """
        Image loaded.
        Source: %s
        Width: %d px
        Height: %d px

        Use the attached image to answer the user's request. Do not call read_image again for this same image unless the user gives a different path or URL.
        """.formatted(path, image.getWidth(), image.getHeight());

        return ToolResult.withImages(prompt, List.of(image));
    }

    private BufferedImage loadImage(String path) throws IOException {
        if (isUrl(path)) {
            return ImageIO.read(URI.create(path).toURL());
        }

        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("File not found: " + path);
        }
        if (!file.isFile()) {
            throw new IOException("Path is not a file: " + path);
        }

        return ImageIO.read(file);
    }

    private boolean isUrl(String path) {
        String lower = path.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
