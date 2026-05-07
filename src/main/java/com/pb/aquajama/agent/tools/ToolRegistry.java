package com.pb.aquajama.agent.tools;

import java.util.List;

public final class ToolRegistry {

    private ToolRegistry() {
    }

    public static List<AgentTool> createDefaultTools() {
        return List.of(
                new EchoTool(),
                new FileBrowserTool(),
                new SystemInfoTool(),
                new ReadImageTool()
        );
    }
}
