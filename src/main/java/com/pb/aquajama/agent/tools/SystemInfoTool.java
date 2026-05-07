package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.sessions.Session;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

public class SystemInfoTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public String getName() {
        return "system_info";
    }

    @Override
    public ObjectNode getDefinition() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");
        ObjectNode command = properties.putObject("command");
        command.put("type", "string");
        command.put("description", "The system information category to retrieve.");
        command.putArray("enum")
                .add("summary")
                .add("time")
                .add("computer")
                .add("os")
                .add("java")
                .add("memory")
                .add("storage");

        parameters.putArray("required").add("command");

        ObjectNode function = MAPPER.createObjectNode();
        function.put("name", getName());
        function.put("description", """
                Retrieve current computer system information available through Java, such as date/time, computer name, OS, Java runtime, memory, processors, and drive space.
                """);
        function.set("parameters", parameters);

        ObjectNode definition = MAPPER.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode arguments, Session session) {
        String command = arguments.path("command").asText("summary");

        return switch (command) {
            case "summary" -> ToolResult.of(summary());
            case "time" -> ToolResult.of(timeInfo());
            case "computer" -> ToolResult.of(computerInfo());
            case "os" -> ToolResult.of(osInfo());
            case "java" -> ToolResult.of(javaInfo());
            case "memory" -> ToolResult.of(memoryInfo());
            case "storage" -> ToolResult.of(storageInfo());
            default -> ToolResult.of("[system_info] Unknown command: " + command);
        };
    }

    private String summary() {
        return """
        System summary

        %s

        %s

        %s

        %s
        """.formatted(timeInfo(), computerInfo(), memoryInfo(), storageInfo());
    }

    private String timeInfo() {
        ZonedDateTime now = ZonedDateTime.now();

        return """
        Current date/time: %s
        Time zone: %s
        UTC offset: %s
        """.formatted(
                DATE_FORMATTER.format(now),
                ZoneId.systemDefault(),
                now.getOffset()
        );
    }

    private String computerInfo() {
        Properties properties = System.getProperties();
        String hostName = hostName();

        return """
        Computer name: %s
        User name: %s
        User home: %s
        Working directory: %s
        Architecture: %s
        Available processors: %d
        """.formatted(
                hostName,
                properties.getProperty("user.name", "unknown"),
                properties.getProperty("user.home", "unknown"),
                properties.getProperty("user.dir", "unknown"),
                properties.getProperty("os.arch", "unknown"),
                Runtime.getRuntime().availableProcessors()
        );
    }

    private String osInfo() {
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        Properties properties = System.getProperties();

        return """
        OS name: %s
        OS version: %s
        OS architecture: %s
        Processors: %d
        System load average: %s
        """.formatted(
                properties.getProperty("os.name", osBean.getName()),
                properties.getProperty("os.version", osBean.getVersion()),
                properties.getProperty("os.arch", osBean.getArch()),
                osBean.getAvailableProcessors(),
                formatLoad(osBean.getSystemLoadAverage())
        );
    }

    private String javaInfo() {
        Properties properties = System.getProperties();
        Runtime runtime = Runtime.getRuntime();

        return """
        Java version: %s
        Java vendor: %s
        Java home: %s
        JVM name: %s
        JVM version: %s
        Class path: %s
        Runtime max memory: %s
        Runtime total memory: %s
        Runtime free memory: %s
        """.formatted(
                properties.getProperty("java.version", "unknown"),
                properties.getProperty("java.vendor", "unknown"),
                properties.getProperty("java.home", "unknown"),
                properties.getProperty("java.vm.name", "unknown"),
                properties.getProperty("java.vm.version", "unknown"),
                properties.getProperty("java.class.path", "unknown"),
                humanSize(runtime.maxMemory()),
                humanSize(runtime.totalMemory()),
                humanSize(runtime.freeMemory())
        );
    }

    private String memoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        StringBuilder result = new StringBuilder();

        result.append("""
        JVM memory
        Max: %s
        Total allocated: %s
        Free in allocated heap: %s
        Used in allocated heap: %s
        """.formatted(
                humanSize(runtime.maxMemory()),
                humanSize(runtime.totalMemory()),
                humanSize(runtime.freeMemory()),
                humanSize(runtime.totalMemory() - runtime.freeMemory())
        ));

        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean extendedBean) {
            result.append("""

            System memory
            Total physical memory: %s
            Free physical memory: %s
            Total swap: %s
            Free swap: %s
            CPU load: %s
            Process CPU load: %s
            """.formatted(
                    humanSize(extendedBean.getTotalMemorySize()),
                    humanSize(extendedBean.getFreeMemorySize()),
                    humanSize(extendedBean.getTotalSwapSpaceSize()),
                    humanSize(extendedBean.getFreeSwapSpaceSize()),
                    formatPercent(extendedBean.getCpuLoad()),
                    formatPercent(extendedBean.getProcessCpuLoad())
            ));
        }

        return result.toString();
    }

    private String storageInfo() {
        File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            return "[system_info] No filesystem roots available";
        }

        return Arrays.stream(roots)
                .map(this::storageLine)
                .collect(Collectors.joining("\n", "Storage\n", ""));
    }

    private String storageLine(File root) {
        long total = root.getTotalSpace();
        long usable = root.getUsableSpace();
        long free = root.getFreeSpace();
        long used = Math.max(0, total - free);

        return """
        Root: %s
          Total: %s
          Used: %s
          Free: %s
          Usable: %s
          Percent used: %s
        """.formatted(
                root.getAbsolutePath(),
                humanSize(total),
                humanSize(used),
                humanSize(free),
                humanSize(usable),
                total <= 0 ? "unknown" : "%.1f%%".formatted((used * 100.0) / total)
        );
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    private String formatLoad(double load) {
        if (load < 0) {
            return "unavailable";
        }
        return "%.2f".formatted(load);
    }

    private String formatPercent(double value) {
        if (value < 0) {
            return "unavailable";
        }
        return "%.1f%%".formatted(value * 100.0);
    }

    private String humanSize(long bytes) {
        if (bytes < 0) {
            return "unavailable";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }

        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        int unit = -1;

        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);

        return "%.1f %s".formatted(Locale.ROOT, value, units[unit]);
    }
}
