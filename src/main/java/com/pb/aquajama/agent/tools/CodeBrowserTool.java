package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pb.aquajama.sessions.Session;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class CodeBrowserTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int DEFAULT_MAX_CHARS = 12_000;
    private static final int HARD_MAX_CHARS = 100_000;
    private static final int DEFAULT_MAX_RESULTS = 50;

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "java", "kt", "kts", "groovy",
            "js", "jsx", "ts", "tsx",
            "html", "css", "scss",
            "xml", "json", "yaml", "yml",
            "properties", "gradle", "md", "txt",
            "sh", "bash", "zsh",
            "go", "py", "rb", "rs", "c", "h", "cpp", "hpp", "cs",
            "sql"
    );

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".idea", ".vscode",
            "target", "build", "out",
            ".gradle", "node_modules",
            "dist", ".next", ".cache"
    );

    private Path projectRoot;

    public CodeBrowserTool() {
        this.projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    @Override
    public String getName() {
        return "code_assistant";
    }

    @Override
    public ObjectNode getDefinition() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        ObjectNode command = properties.putObject("command");
        command.put("type", "string");
        command.put("description", """
                What to do with the source code. Typical workflow: inspect_project, search_code or find_references,
                display_file, then edit_file or create_or_replace_file when the user asks for code changes.
                """);
        command.putArray("enum")
                .add("inspect_project")
                .add("list_files")
                .add("display_file")
                .add("search_code")
                .add("find_references")
                .add("review_project")
                .add("review_file")
                .add("propose_change")
                .add("edit_file")
                .add("create_or_replace_file")
                .add("set_root");

        properties.putObject("path")
                .put("type", "string")
                .put("description", """
                        Source file or folder path, relative to the project root unless absolute. Use with display_file,
                        review_file, edit_file, create_or_replace_file, set_root, list_files, and optional search roots.
                        """);

        properties.putObject("query")
                .put("type", "string")
                .put("description", "Text to search for in source files. Use with search_code.");

        properties.putObject("symbol")
                .put("type", "string")
                .put("description", "Class, method, field, package, or other symbol to find definitions/usages/imports for. Use with find_references.");

        properties.putObject("max_chars")
                .put("type", "integer")
                .put("description", "Maximum characters to return when reading a file. Defaults to 12000.");

        properties.putObject("max_results")
                .put("type", "integer")
                .put("description", "Maximum search or tree results to return. Defaults to 50.");

        properties.putObject("instructions")
                .put("type", "string")
                .put("description", "User request, review goal, or desired code change. Use with review_project, review_file, and propose_change.");

        properties.putObject("old_text")
                .put("type", "string")
                .put("description", "Exact existing source text to replace. Required for edit_file. Include enough context to match one place.");

        properties.putObject("new_text")
                .put("type", "string")
                .put("description", "Replacement source text for edit_file, or full file contents for create_or_replace_file.");

        properties.putObject("replace_all")
                .put("type", "boolean")
                .put("description", "When true, edit_file replaces all exact occurrences. Defaults to false.");

        properties.putObject("overwrite")
                .put("type", "boolean")
                .put("description", "When true, create_or_replace_file may replace an existing file. Defaults to false.");

        parameters.putArray("required").add("command");

        ObjectNode function = MAPPER.createObjectNode();
        function.put("name", getName());
        function.put("description", """
                Source code assistant for the current project. Use this tool whenever the user asks to inspect,
                understand, review, search, trace references, display source, suggest code changes, or modify project files.
                It can read source files with line numbers and can apply exact edits or write source files inside the project root.
                Do not merely describe code changes when the user asks you to modify code: use edit_file or create_or_replace_file.
                """);
        function.set("parameters", parameters);

        ObjectNode definition = MAPPER.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode arguments, Session session) throws Exception {
        String command = arguments.path("command").asText("");

        return switch (command) {
            case "set_root" -> ToolResult.of(setRoot(arguments.path("path").asText("")));
            case "inspect_project" -> ToolResult.of(scanProject());
            case "list_files" -> ToolResult.of(tree(
                    arguments.path("path").asText(""),
                    arguments.path("max_results").asInt(DEFAULT_MAX_RESULTS)
            ));
            case "display_file" -> ToolResult.of(readFile(
                    arguments.path("path").asText(""),
                    arguments.path("max_chars").asInt(DEFAULT_MAX_CHARS)
            ));
            case "search_code" -> ToolResult.of(search(
                    arguments.path("query").asText(""),
                    arguments.path("path").asText(""),
                    arguments.path("max_results").asInt(DEFAULT_MAX_RESULTS)
            ));
            case "find_references" -> ToolResult.of(references(
                    arguments.path("symbol").asText(""),
                    arguments.path("path").asText(""),
                    arguments.path("max_results").asInt(DEFAULT_MAX_RESULTS)
            ));
            case "review_project" -> ToolResult.of(projectReview(
                    arguments.path("instructions").asText(""),
                    arguments.path("max_results").asInt(DEFAULT_MAX_RESULTS)
            ));
            case "review_file" -> ToolResult.of(reviewFile(
                    arguments.path("path").asText(""),
                    arguments.path("instructions").asText("")
            ));
            case "propose_change" -> ToolResult.of(suggestPatch(
                    arguments.path("path").asText(""),
                    arguments.path("instructions").asText("")
            ));
            case "edit_file" -> ToolResult.of(applyEdit(
                    arguments.path("path").asText(""),
                    arguments.path("old_text").asText(""),
                    arguments.path("new_text").asText(""),
                    arguments.path("replace_all").asBoolean(false)
            ));
            case "create_or_replace_file" -> ToolResult.of(writeFile(
                    arguments.path("path").asText(""),
                    arguments.path("new_text").asText(""),
                    arguments.path("overwrite").asBoolean(false)
            ));
            default -> ToolResult.of("[code_assistant] Unknown command: " + command);
        };
    }

    private String setRoot(String path) {
        if (path.isBlank()) {
            return "[code_assistant] Missing path for set_root.";
        }

        Path candidate = Paths.get(path).toAbsolutePath().normalize();

        if (!Files.exists(candidate)) {
            return "[code_assistant] Project root does not exist: " + candidate;
        }

        if (!Files.isDirectory(candidate)) {
            return "[code_assistant] Project root is not a directory: " + candidate;
        }

        projectRoot = candidate;

        return """
        [code_assistant] Project root set.

        Root: %s
        """.formatted(projectRoot);
    }

    private String scanProject() throws IOException {
        List<Path> files = sourceFiles(projectRoot, 500);

        String buildSystem = detectBuildSystem();
        String sourceRoots = detectSourceRoots();

        String interestingFiles = files.stream()
                .limit(80)
                .map(this::relative)
                .map(Path::toString)
                .collect(Collectors.joining("\n- ", "- ", ""));

        if (interestingFiles.equals("- ")) {
            interestingFiles = "(no source files found)";
        }

        return """
        Project scan

        Root:
        %s

        Build system:
        %s

        Source roots:
        %s

        Source files found:
        %d%s

        Interesting files:
        %s
        """.formatted(
                projectRoot,
                buildSystem,
                sourceRoots,
                files.size(),
                files.size() >= 500 ? " or more" : "",
                interestingFiles
        );
    }

    private String tree(String path, int maxResults) throws IOException {
        Path root = path.isBlank() ? projectRoot : resolveInsideRoot(path);

        if (!Files.exists(root)) {
            return "[code_assistant] Path not found: " + path;
        }

        if (!Files.isDirectory(root)) {
            return "[code_assistant] Path is not a directory: " + relative(root);
        }

        int limit = safeLimit(maxResults, 1, 500);

        List<Path> entries = new ArrayList<>();

        try (var stream = Files.walk(root)) {
            stream
                    .filter(p -> !p.equals(root))
                    .filter(this::isNotInSkippedDirectory)
                    .filter(p -> Files.isDirectory(p) || isSourceLikeFile(p))
                    .sorted()
                    .limit(limit)
                    .forEach(entries::add);
        }

        String body = entries.stream()
                .map(p -> {
                    String prefix = Files.isDirectory(p) ? "[DIR]  " : "[FILE] ";
                    return prefix + relative(p);
                })
                .collect(Collectors.joining("\n"));

        if (body.isBlank()) {
            body = "(no source files found)";
        }

        return """
        Project tree

        Root:
        %s

        Listing:
        %s
        """.formatted(relative(root), body);
    }

    private String readFile(String path, int maxChars) throws IOException {
        Path file = resolveInsideRoot(path);

        if (!Files.exists(file)) {
            return "[code_assistant] File not found: " + path;
        }

        if (!Files.isRegularFile(file)) {
            return "[code_assistant] Path is not a regular file: " + relative(file);
        }

        if (!isSourceLikeFile(file)) {
            return "[code_assistant] Refusing to read non-source or likely-binary file: " + relative(file);
        }

        String content = readUtf8(file);
        int limit = safeLimit(maxChars, 1, HARD_MAX_CHARS);
        boolean truncated = content.length() > limit;

        if (truncated) {
            content = content.substring(0, limit);
        }

        String numbered = withLineNumbers(content);

        return """
        File:
        %s

        Size:
        %d bytes

        Content:
        %s%s
        """.formatted(
                relative(file),
                Files.size(file),
                numbered,
                truncated ? "\n... (truncated)" : ""
        );
    }

    private String search(String query, String path, int maxResults) throws IOException {
        if (query.isBlank()) {
            return "[code_assistant] Missing query for search.";
        }

        Path root = path.isBlank() ? projectRoot : resolveInsideRoot(path);

        if (!Files.exists(root)) {
            return "[code_assistant] Search path not found: " + path;
        }

        int limit = safeLimit(maxResults, 1, 200);
        List<String> matches = new ArrayList<>();

        for (Path file : sourceFiles(root, 2_000)) {
            if (matches.size() >= limit) {
                break;
            }

            String content;
            try {
                content = readUtf8(file);
            } catch (IOException e) {
                continue;
            }

            String[] lines = content.split("\\R", -1);

            for (int i = 0; i < lines.length && matches.size() < limit; i++) {
                String line = lines[i];
                if (line.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                    matches.add("%s:%d: %s".formatted(relative(file), i + 1, line.strip()));
                }
            }
        }

        if (matches.isEmpty()) {
            return """
            Search results

            Query:
            %s

            No matches found.
            """.formatted(query);
        }

        return """
        Search results

        Query:
        %s

        Matches:
        %s
        """.formatted(query, String.join("\n", matches));
    }

    private String references(String symbol, String path, int maxResults) throws IOException {
        if (symbol.isBlank()) {
            return "[code_assistant] Missing symbol for references.";
        }

        Path root = path.isBlank() ? projectRoot : resolveInsideRoot(path);

        if (!Files.exists(root)) {
            return "[code_assistant] Reference search path not found: " + path;
        }

        int limit = safeLimit(maxResults, 1, 300);
        List<String> matches = new ArrayList<>();

        for (Path file : sourceFiles(root, 3_000)) {
            if (matches.size() >= limit) {
                break;
            }

            String content;
            try {
                content = readUtf8(file);
            } catch (IOException e) {
                continue;
            }

            String[] lines = content.split("\\R", -1);

            for (int i = 0; i < lines.length && matches.size() < limit; i++) {
                String line = lines[i];
                if (containsSymbol(line, symbol)) {
                    matches.add("%s:%d: %s %s".formatted(
                            relative(file),
                            i + 1,
                            referenceKind(line, symbol),
                            line.strip()
                    ));
                }
            }
        }

        if (matches.isEmpty()) {
            return """
            Reference search

            Symbol:
            %s

            No references found.
            """.formatted(symbol);
        }

        return """
        Reference search

        Symbol:
        %s

        References:
        %s
        """.formatted(symbol, String.join("\n", matches));
    }

    private String projectReview(String instructions, int maxResults) throws IOException {
        List<Path> files = sourceFiles(projectRoot, 2_000);
        int limit = safeLimit(maxResults, 10, 200);

        List<String> interesting = files.stream()
                .limit(limit)
                .map(this::relative)
                .map(Path::toString)
                .toList();

        List<String> todoMatches = findTodoLikeComments(files, 30);

        return """
        Project review request

        Root:
        %s

        User instructions:
        %s

        Build system:
        %s

        Source roots:
        %s

        Source files found:
        %d%s

        Representative source files:
        %s

        TODO/FIXME style comments:
        %s

        Review guidance for the model:
        - Start from the user's requested goal.
        - Use search, references, and read before proposing edits.
        - Prefer small, exact edit_file calls for modifications.
        - Use create_or_replace_file only for new files or full-file rewrites that are clearly justified.
        - After editing, summarize changed files and recommend build/test commands.
        """.formatted(
                projectRoot,
                instructions.isBlank() ? "(general project review)" : instructions,
                detectBuildSystem(),
                detectSourceRoots(),
                files.size(),
                files.size() >= 2_000 ? " or more" : "",
                interesting.isEmpty() ? "(no source files found)" : "- " + String.join("\n- ", interesting),
                todoMatches.isEmpty() ? "(none found)" : String.join("\n", todoMatches)
        );
    }

    private String reviewFile(String path, String instructions) throws IOException {
        Path file = resolveInsideRoot(path);

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return "[code_assistant] File not found: " + path;
        }

        if (!isSourceLikeFile(file)) {
            return "[code_assistant] Refusing to review non-source file: " + relative(file);
        }

        String content = readUtf8(file);
        String numbered = withLineNumbers(content.length() > HARD_MAX_CHARS
                ? content.substring(0, HARD_MAX_CHARS) + "\n... (truncated)"
                : content);

        return """
        Code review request

        File:
        %s

        User instructions:
        %s

        Review guidance for the model:
        - Identify bugs, design issues, missing validation, unsafe assumptions, and maintainability problems.
        - Refer to specific line numbers.
        - Suggest concrete changes.
        - For actual modifications, use edit_file or create_or_replace_file after identifying the exact change.

        Source:
        %s
        """.formatted(
                relative(file),
                instructions.isBlank() ? "(general review)" : instructions,
                numbered
        );
    }

    private String suggestPatch(String path, String instructions) throws IOException {
        Path file = resolveInsideRoot(path);

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return "[code_assistant] File not found: " + path;
        }

        if (!isSourceLikeFile(file)) {
            return "[code_assistant] Refusing to patch non-source file: " + relative(file);
        }

        String content = readUtf8(file);
        String numbered = withLineNumbers(content.length() > HARD_MAX_CHARS
                ? content.substring(0, HARD_MAX_CHARS) + "\n... (truncated)"
                : content);

        return """
        Patch suggestion request

        File:
        %s

        User instructions:
        %s

        Important:
        This command is for planning only. To modify code, call edit_file or create_or_replace_file after choosing the exact change.

        Preferred response format:
        1. Explain the change briefly.
        2. Provide a unified diff patch if possible.
        3. Mention any follow-up files that may also need changes.

        Current source:
        %s
        """.formatted(
                relative(file),
                instructions.isBlank() ? "(no specific instructions provided)" : instructions,
                numbered
        );
    }

    private String applyEdit(String path, String oldText, String newText, boolean replaceAll) throws IOException {
        Path file = resolveInsideRoot(path);

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return "[code_assistant] File not found: " + path;
        }

        if (!isSourceLikeFile(file)) {
            return "[code_assistant] Refusing to edit non-source file: " + relative(file);
        }

        if (oldText.isEmpty()) {
            return "[code_assistant] Missing old_text for edit_file.";
        }

        String content = readUtf8(file);
        int occurrences = countOccurrences(content, oldText);

        if (occurrences == 0) {
            return "[code_assistant] old_text was not found in " + relative(file);
        }

        if (occurrences > 1 && !replaceAll) {
            return """
            [code_assistant] old_text matched %d times in %s.
            Set replace_all=true, or provide a more specific old_text snippet.
            """.formatted(occurrences, relative(file));
        }

        String updated = replaceAll
                ? content.replace(oldText, newText)
                : content.replaceFirst(java.util.regex.Pattern.quote(oldText), java.util.regex.Matcher.quoteReplacement(newText));

        Files.writeString(file, updated, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

        return """
        [code_assistant] Edit applied.

        File:
        %s

        Replacements:
        %d

        Size before:
        %d chars

        Size after:
        %d chars
        """.formatted(
                relative(file),
                replaceAll ? occurrences : 1,
                content.length(),
                updated.length()
        );
    }

    private String writeFile(String path, String content, boolean overwrite) throws IOException {
        if (path == null || path.isBlank()) {
            return "[code_assistant] Missing path for create_or_replace_file.";
        }

        Path file = resolveInsideRoot(path);

        if (Files.exists(file) && !overwrite) {
            return "[code_assistant] File already exists. Set overwrite=true to replace: " + relative(file);
        }

        if (Files.exists(file) && !Files.isRegularFile(file)) {
            return "[code_assistant] Path is not a regular file: " + relative(file);
        }

        if (!isSourcePath(file)) {
            return "[code_assistant] Refusing to write non-source-like path: " + relative(file);
        }

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(
                file,
                content == null ? "" : content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        return """
        [code_assistant] File written.

        File:
        %s

        Size:
        %d chars
        """.formatted(relative(file), content == null ? 0 : content.length());
    }

    private List<Path> sourceFiles(Path root, int maxFiles) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }

        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isNotInSkippedDirectory)
                    .filter(this::isSourceLikeFile)
                    .sorted()
                    .limit(maxFiles)
                    .toList();
        }
    }

    private String detectBuildSystem() {
        List<String> detected = new ArrayList<>();

        if (Files.exists(projectRoot.resolve("pom.xml"))) {
            detected.add("Maven");
        }
        if (Files.exists(projectRoot.resolve("build.gradle")) || Files.exists(projectRoot.resolve("build.gradle.kts"))) {
            detected.add("Gradle");
        }
        if (Files.exists(projectRoot.resolve("package.json"))) {
            detected.add("npm/package.json");
        }
        if (Files.exists(projectRoot.resolve("go.mod"))) {
            detected.add("Go module");
        }

        return detected.isEmpty() ? "unknown" : String.join(", ", detected);
    }

    private String detectSourceRoots() {
        List<String> roots = List.of(
                "src/main/java",
                "src/test/java",
                "src/main/kotlin",
                "src/test/kotlin",
                "src/main/resources",
                "src/test/resources",
                "src",
                "app",
                "lib"
        );

        List<String> existing = roots.stream()
                .filter(r -> Files.isDirectory(projectRoot.resolve(r)))
                .toList();

        if (existing.isEmpty()) {
            return "(none detected)";
        }

        return existing.stream()
                .map(r -> "- " + r)
                .collect(Collectors.joining("\n"));
    }

    private Path resolveInsideRoot(String path) {
        if (path == null || path.isBlank()) {
            return projectRoot;
        }

        Path raw = Paths.get(path);
        Path resolved = raw.isAbsolute()
                ? raw.toAbsolutePath().normalize()
                : projectRoot.resolve(raw).toAbsolutePath().normalize();

        if (!resolved.startsWith(projectRoot)) {
            throw new IllegalArgumentException("[code_assistant] Refusing to access path outside project root: " + path);
        }

        return resolved;
    }

    private Path relative(Path path) {
        Path normalized = path.toAbsolutePath().normalize();

        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized);
        }

        return normalized;
    }

    private boolean isNotInSkippedDirectory(Path path) {
        Path relative = relative(path);

        for (Path part : relative) {
            if (SKIPPED_DIRECTORIES.contains(part.toString())) {
                return false;
            }
        }

        return true;
    }

    private boolean isSourceLikeFile(Path path) {
        String fileName = path.getFileName().toString();

        if (isSpecialSourceFileName(fileName)) {
            return true;
        }

        String extension = extension(fileName);

        if (!SOURCE_EXTENSIONS.contains(extension)) {
            return false;
        }

        return looksLikeText(path);
    }

    private boolean isSourcePath(Path path) {
        String fileName = path.getFileName().toString();

        if (isSpecialSourceFileName(fileName)) {
            return true;
        }

        return SOURCE_EXTENSIONS.contains(extension(fileName));
    }

    private boolean isSpecialSourceFileName(String fileName) {
        return fileName.equals("Dockerfile") ||
                fileName.equals("Makefile") ||
                fileName.equals(".gitignore") ||
                fileName.equals(".env.example");
    }

    private String readUtf8(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            throw new IOException("[code_assistant] File is not valid UTF-8 text: " + relative(file), e);
        }
    }

    private boolean looksLikeText(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return false;
            }

            long size = Files.size(path);
            if (size > 2_000_000) {
                return false;
            }

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
            }
        } catch (IOException e) {
            return false;
        }
    }

    private String withLineNumbers(String content) {
        String[] lines = content.split("\\R", -1);
        StringBuilder result = new StringBuilder();

        int width = String.valueOf(lines.length).length();

        for (int i = 0; i < lines.length; i++) {
            result.append("%" + width + "d | %s%n".formatted(i + 1, lines[i]));
        }

        return result.toString();
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');

        if (dot == -1 || dot == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private int safeLimit(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private boolean containsSymbol(String line, String symbol) {
        if (symbol.isBlank()) {
            return false;
        }

        if (symbol.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(symbol) + "\\b")
                    .matcher(line)
                    .find();
        }

        return line.contains(symbol);
    }

    private String referenceKind(String line, String symbol) {
        String stripped = line.strip();

        if (stripped.startsWith("import ") && stripped.contains(symbol)) {
            return "[import]";
        }
        if (stripped.contains("class " + symbol) || stripped.contains("interface " + symbol) || stripped.contains("record " + symbol) || stripped.contains("enum " + symbol)) {
            return "[declaration]";
        }
        if (stripped.contains("new " + symbol + "(")) {
            return "[construction]";
        }
        if (stripped.startsWith("@") && stripped.contains(symbol)) {
            return "[annotation]";
        }

        return "[reference]";
    }

    private List<String> findTodoLikeComments(List<Path> files, int maxResults) {
        List<String> matches = new ArrayList<>();

        for (Path file : files) {
            if (matches.size() >= maxResults) {
                break;
            }

            String content;
            try {
                content = readUtf8(file);
            } catch (IOException e) {
                continue;
            }

            String[] lines = content.split("\\R", -1);
            for (int i = 0; i < lines.length && matches.size() < maxResults; i++) {
                String normalized = lines[i].toLowerCase(Locale.ROOT);
                if (normalized.contains("todo") || normalized.contains("fixme") || normalized.contains("hack")) {
                    matches.add("%s:%d: %s".formatted(relative(file), i + 1, lines[i].strip()));
                }
            }
        }

        return matches;
    }

    private int countOccurrences(String content, String needle) {
        int count = 0;
        int index = 0;

        while ((index = content.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }

        return count;
    }
}
