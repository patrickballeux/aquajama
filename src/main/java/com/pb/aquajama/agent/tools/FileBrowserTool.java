package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.sessions.Session;

import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class FileBrowserTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_READ_LIMIT = 8000;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "bat", "c", "cfg", "conf", "cpp", "cs", "css", "csv", "env",
            "gitignore", "gradle", "h", "html", "ini", "java", "js", "json",
            "log", "md", "properties", "py", "rb", "rs", "sh", "sql", "ts",
            "tsx", "txt", "xml", "yaml", "yml"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private File currentDirectory;

    public FileBrowserTool() {
        this.currentDirectory = new File(System.getProperty("user.home"));
    }

    @Override
    public String getName() {
        return "file_browser";
    }

    @Override
    public ObjectNode getDefinition() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode command = properties.putObject("command");
        command.put("type", "string");
        command.put("description", "The file browser operation to perform.");
        command.putArray("enum").add("list").add("cd").add("info").add("read");

        properties.putObject("path")
                .put("type", "string")
                .put("description", "Absolute path or path relative to the current directory. Required for cd, info, and read.");

        properties.putObject("max_chars")
                .put("type", "integer")
                .put("description", "Maximum characters to return when reading a text file. Defaults to 8000.");

        parameters.putArray("required").add("command");

        ObjectNode function = MAPPER.createObjectNode();
        function.put("name", getName());
        function.put("description", """
                Navigate and read files on the computer. The current directory starts at the user's home folder. Use list before navigating. Avoid system files unless the user explicitly asks for them.
                """);
        function.set("parameters", parameters);

        ObjectNode definition = MAPPER.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode action, Session session) throws Exception {

        String command = action.path("command").asText("");

        return switch (command) {
            case "list" -> ToolResult.of(listFiles());
            case "cd" -> ToolResult.of(changeDirectory(action.path("path").asText()));
            case "info" -> ToolResult.of(fileInfo(action.path("path").asText()));
            case "read" -> ToolResult.of(readFile(
                    action.path("path").asText(),
                    action.path("max_chars").asInt(DEFAULT_READ_LIMIT)
            ));
            default -> ToolResult.of("[file_browser] Unknown command: " + command);
        };
    }

    // --------------------------------------------------------
    private String listFiles() {

        File[] files = currentDirectory.listFiles();

        if (files == null) {
            return "[file_browser] Unable to read directory";
        }

        String result = Arrays.stream(files)
                .map(this::formatListEntry)
                .sorted()
                .collect(Collectors.joining("\n"));

        return """
        Current directory:
        %s

        Contents:
        %s
        """.formatted(currentDirectory.getAbsolutePath(), result);
    }

    private String changeDirectory(String path) {

        File newDir = resolve(path);

        if (!newDir.exists() || !newDir.isDirectory()) {
            return "[file_browser] Directory not found: " + path;
        }

        currentDirectory = newDir;

        return "[file_browser] Now in: " + currentDirectory.getAbsolutePath();
    }

    private String fileInfo(String path) throws IOException {

        File file = resolve(path);

        if (!file.exists()) {
            return "[file_browser] Path not found: " + path;
        }

        Path filePath = file.toPath();
        BasicFileAttributes attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
        String contentType = Files.probeContentType(filePath);

        return """
        Path: %s
        Name: %s
        Kind: %s
        File type: %s
        Size: %s (%d bytes)
        Created: %s
        Last modified: %s
        Last accessed: %s
        Readable as text: %s
        """.formatted(
                file.getAbsolutePath(),
                file.getName(),
                kind(attributes),
                contentType == null ? extensionOrUnknown(file) : contentType,
                humanSize(attributes.size()),
                attributes.size(),
                formatTime(attributes.creationTime().toInstant()),
                formatTime(attributes.lastModifiedTime().toInstant()),
                formatTime(attributes.lastAccessTime().toInstant()),
                isTextFile(filePath, contentType)
        );
    }

    private String readFile(String path, int maxChars) throws Exception {

        File file = resolve(path);

        if (!file.exists() || !file.isFile()) {
            return "[file_browser] File not found: " + path;
        }

        Path filePath = file.toPath();
        String contentType = Files.probeContentType(filePath);

        if (!isTextFile(filePath, contentType)) {
            return """
            [file_browser] Refusing to read likely-binary file as text.
            Path: %s
            File type: %s
            Use the info command for metadata.
            """.formatted(file.getAbsolutePath(), contentType == null ? extensionOrUnknown(file) : contentType);
        }

        String content;
        try {
            content = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            return "[file_browser] File is not valid UTF-8 text: " + file.getAbsolutePath();
        }

        int readLimit = Math.max(1, Math.min(maxChars, 100_000));
        if (content.length() > readLimit) {
            content = content.substring(0, readLimit) + "\n... (truncated)";
        }

        return """
        File: %s
        Path: %s
        Type: %s
        Size: %s

        %s
        """.formatted(
                file.getName(),
                file.getAbsolutePath(),
                contentType == null ? extensionOrUnknown(file) : contentType,
                humanSize(Files.size(filePath)),
                content
        );
    }

    private File resolve(String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(currentDirectory, path);
    }

    private String formatListEntry(File file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            String prefix = attributes.isDirectory() ? "[DIR] " : "[FILE]";
            String size = attributes.isDirectory() ? "-" : humanSize(attributes.size());
            return "%s %-10s %s  %s".formatted(
                    prefix,
                    size,
                    formatTime(attributes.lastModifiedTime().toInstant()),
                    file.getName()
            );
        } catch (IOException e) {
            return (file.isDirectory() ? "[DIR] " : "[FILE]") + "          " + file.getName();
        }
    }

    private String kind(BasicFileAttributes attributes) {
        if (attributes.isDirectory()) {
            return "directory";
        }
        if (attributes.isRegularFile()) {
            return "regular file";
        }
        if (attributes.isSymbolicLink()) {
            return "symbolic link";
        }
        return "other";
    }

    private boolean isTextFile(Path path, String contentType) {
        if (contentType != null) {
            String normalized = contentType.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("text/")) {
                return true;
            }
            if (normalized.contains("json") || normalized.contains("xml") || normalized.contains("yaml")) {
                return true;
            }
        }

        if (TEXT_EXTENSIONS.contains(extension(path.getFileName().toString()))) {
            return true;
        }

        return looksLikeText(path);
    }

    private boolean looksLikeText(Path path) {
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = input.readNBytes(4096);
            if (buffer.length == 0) {
                return true;
            }

            int suspicious = 0;
            for (byte b : buffer) {
                int value = b & 0xff;
                if (value == 0) {
                    return false;
                }
                if (value < 32 && value != '\n' && value != '\r' && value != '\t') {
                    suspicious++;
                }
            }

            return suspicious < Math.max(1, buffer.length / 20);
        } catch (IOException e) {
            return false;
        }
    }

    private String extensionOrUnknown(File file) {
        String extension = extension(file.getName());
        return extension.isBlank() ? "unknown" : extension + " file";
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;

        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);

        return "%.1f %s".formatted(value, units[unit]);
    }

    private String formatTime(java.time.Instant instant) {
        return DATE_FORMATTER.format(instant.atZone(ZoneId.systemDefault()));
    }
}
