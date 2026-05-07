package com.pb.aquajama.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.agent.tools.AgentTool;
import com.pb.aquajama.sessions.Message;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class Client {

    public static final String LOCAL_URL = "http://localhost:11434";

    private final String url;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private final OllamaModelService modelService;
    private final OllamaStreamHandler streamHandler;

    public Client(String url) {

        this.url = url;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.modelService = new OllamaModelService(url, httpClient);
        this.streamHandler = new OllamaStreamHandler(url, httpClient);
    }

    public List<Model> getModels() {
        return modelService.getModels();
    }

    public Optional<Model> findSmallestModelWith(Model.Capability capability) {
        return modelService.findSmallestModelWith(capability);
    }

    public void sendMessages(
            Model model,
            List<Message> history,
            List<AgentTool> tools,
            boolean stream,
            StreamListener listener
    ) {

        String body = buildChatRequest(
                model,
                history,
                tools,
                stream
        );

        streamHandler.stream(body, listener);
    }

    private String buildChatRequest(
            Model model,
            List<Message> history,
            List<AgentTool> tools,
            boolean stream
    ) {

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model.name());

        ArrayNode messages = body.putArray("messages");
        for (Message msg : history) {
            ObjectNode message = messages.addObject();
            message.put("role", msg.role());
            message.put("content", msg.content());

            if (msg.toolName() != null) {
                message.put("tool_name", msg.toolName());
            }

            if (!msg.toolCalls().isEmpty()) {
                ArrayNode toolCalls = message.putArray("tool_calls");
                msg.toolCalls().forEach(toolCalls::add);
            }

            List<String> encodedImages = ImageEncoder.encode(msg.images());
            if (!encodedImages.isEmpty()) {
                ArrayNode images = message.putArray("images");
                encodedImages.forEach(images::add);
            }
        }

        if (model.canUseTools() && !tools.isEmpty()) {
            ArrayNode toolDefinitions = body.putArray("tools");
            tools.stream()
                    .filter(tool -> !tool.requiresVision() || model.canUseVision())
                    .map(AgentTool::getDefinition)
                    .forEach(toolDefinitions::add);
        }

        if (model.canThink()) {
            ObjectNode options = body.putObject("options");
            options.put("think", true);
        }

        body.put("stream", stream);

        return body.toString();
    }
}
