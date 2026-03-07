package com.pb.aquajama.ollama;

public interface StreamListener {
    void onToken(Token token);
    void onComplete(Throwable error);
}
