package com.pb.aquajama.agent.tools;

import java.awt.image.BufferedImage;
import java.util.List;

public record ToolResult(String content, List<BufferedImage> images) {

    public static ToolResult of(String content) {
        return new ToolResult(content == null ? "" : content, List.of());
    }

    public static ToolResult withImages(String content, List<BufferedImage> images) {
        return new ToolResult(content == null ? "" : content, List.copyOf(images));
    }
}
