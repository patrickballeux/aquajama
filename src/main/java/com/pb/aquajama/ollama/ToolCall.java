package com.pb.aquajama.ollama;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolCall(String id, String name, JsonNode arguments, JsonNode raw) {
}
