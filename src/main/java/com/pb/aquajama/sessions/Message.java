package com.pb.aquajama.sessions;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.util.List;

public class Message {

    private final String role;
    private final String content;
    private final String toolName;
    private final List<JsonNode> toolCalls;
    private final List<BufferedImage> images;

    public Message(String role, String content) {
        this(role, content, null, List.of(), List.of());
    }

    private Message(
            String role,
            String content,
            String toolName,
            List<JsonNode> toolCalls,
            List<BufferedImage> images
    ) {
        this.role = role;
        this.content = content;
        this.toolName = toolName;
        this.toolCalls = List.copyOf(toolCalls);
        this.images = List.copyOf(images);
    }

    public static Message assistantToolCall(String content, List<JsonNode> toolCalls) {
        return new Message("assistant", content, null, toolCalls, List.of());
    }

    public static Message toolResult(String toolName, String content) {
        return new Message("tool", content, toolName, List.of(), List.of());
    }

    public static Message userWithImages(String content, List<BufferedImage> images) {
        return new Message("user", content, null, List.of(), images);
    }

    public String role() {
        return role;
    }

    public String content() {
        return content;
    }

    public String toolName() {
        return toolName;
    }

    public List<JsonNode> toolCalls() {
        return toolCalls;
    }

    public List<BufferedImage> images() {
        return images;
    }
}
