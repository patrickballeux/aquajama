package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.aquajama.ollama.Token;
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
    public String getActionName() {
        return "read_image";
    }

    public String getDescription() {
        return "Loads an image from a file path or URL so the AI can analyze or describe it.";
    }

    @Override
    public String buildRuleSnippet() {
        return """
               read_image: Load an image for analysis.
               Use this tool when the user asks to describe, analyze,
               or answer questions about an image.

               Provide a JSON command like:
               { "action": "read_image", "path": "/full/path/to/image.png" }
               or:
               { "action": "read_image", "path": "https://example.com/image.jpg" }
               """;
    }

    @Override
    public boolean supports(JsonNode action) {
        return "read_image".equals(action.path("action").asText());
    }

    /**
     * New contract used by Session: the tool talks directly to Client via
     * Session.
     */
    @Override
    public void execute(JsonNode action, Session session) throws Exception {
        String path = action.path("path").asText();
        if (path == null || path.isEmpty()) {
            sendToUi(session, "[read_image] Missing 'path' parameter.\n");
            return;
        }

        BufferedImage image;
        try {
            image = loadImage(path);
        } catch (IOException e) {
            sendToUi(session, "[read_image] Unable to load image from: " + path + " (" + e.getMessage() + ")\n");
            return;
        }

        if (image == null) {
            sendToUi(session, "[read_image] Unable to load image from: " + path + "\n");
            return;
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

        session.getClient().sendPrompt(
                session.model,
                session.getDefaultSystemPrompt(),
                userPrompt,
                List.of(image),
                true,
                session // Session is still the StreamListener back to the UI
        );
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

    private void sendToUi(Session session, String msg) {
        if (session.getUiConsumer() != null) {
            session.getUiConsumer().accept(new Token(msg, false));
        }
    }
}
