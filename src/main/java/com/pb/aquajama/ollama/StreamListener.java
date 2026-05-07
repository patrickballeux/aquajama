package com.pb.aquajama.ollama;

public interface StreamListener {
    void onToken(Token token);
    void onToolCall(ToolCall toolCall);
    void onComplete(Throwable error);
}
