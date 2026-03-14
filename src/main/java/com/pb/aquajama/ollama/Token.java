package com.pb.aquajama.ollama;

public record Token(String text, boolean isThinking, boolean fromUser) { }
