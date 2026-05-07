package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.sessions.Session;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Tool that loads one image (local file or HTTP/HTTPS URL) and sends it to the
 * current model via Client/Session for analysis.
 */
public class ReadImageTool implements AgentTool {

    private final ObjectMapper mapper;

    public ReadImageTool(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String getName() {
        return "read_image";
    }

    public String getDescription() {
        return "Loads an image from a file path or URL so the AI can analyze or describe it.";
    }

    @Override
    public ObjectNode getDefinition() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("path")
                .put("type", "string")
                .put("description", "A local image path or HTTP/HTTPS image URL.");
        parameters.putArray("required").add("path");

        ObjectNode function = mapper.createObjectNode();
        function.put("name", getName());
        function.put("description", "Load an image for analysis.");
        function.set("parameters", parameters);

        ObjectNode definition = mapper.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    /**
     * New contract used by Session: the tool talks directly to Client via
     * Session.
     */
    @Override
    public ToolResult execute(JsonNode action, Session session) throws Exception {
        String path = action.path("path").asText();
        if (path == null || path.isEmpty()) {
            return ToolResult.of("[read_image] Missing 'path' parameter.");
        }

        BufferedImage image;
        try {
            image = loadImage(path);
        } catch (IOException e) {
            return ToolResult.of("[read_image] Unable to load image from: " + path + " (" + e.getMessage() + ")");
        }

        if (image == null) {
            return ToolResult.of("[read_image] Unable to load image from: " + path);
        }

        String userPrompt = """
You asked to analyze an image to answer the user's question.

User question:
"%s"

The image is attached to this message.

Instructions:
- Do NOT call any tools.
- Do NOT output JSON.
- Answer the question using the image.
""".formatted(session.getLastUserPrompt());

        session.sendToolResult(userPrompt, List.of(image));        
        return ToolResult.of("Image loaded for analysis.");
    }

    // ------------------------------------------------------------------------
    private BufferedImage loadImage(String pathOrUrl) throws IOException {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return ImageIO.read(new URL(pathOrUrl));
        } else {
            File file = new File(pathOrUrl);
            if (!file.exists()) {
                throw new IOException("File not found: " + pathOrUrl);
            }
            return ImageIO.read(file);
        }
    }
}
