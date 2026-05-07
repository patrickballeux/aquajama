package com.pb.aquajama.agent.tools;

public record ToolResult(String content) {

    public static ToolResult of(String content) {
        return new ToolResult(content == null ? "" : content);
    }
}
