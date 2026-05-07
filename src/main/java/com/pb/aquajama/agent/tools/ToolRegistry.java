package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public final class ToolRegistry {

    private ToolRegistry() {
    }

    public static List<AgentTool> createDefaultTools(ObjectMapper mapper) {
        return List.of(
                new EchoTool(),
                new FileBrowserTool(),
                new SystemInfoTool()
        );
    }
}
