package com.pb.aquajama.sessions;

public class Message {

    private final String role;
    private final String content;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String role() {
        return role;
    }

    public String content() {
        return content;
    }
}