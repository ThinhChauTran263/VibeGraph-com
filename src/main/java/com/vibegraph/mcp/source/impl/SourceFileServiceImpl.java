package com.vibegraph.mcp.source.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.mcp.source.SourceFileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Filesystem-only implementation of {@link SourceFileService}. Holds no graph knowledge —
 * it just enforces the security contract over a project's import root.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SourceFileServiceImpl implements SourceFileService {

    static final int MAX_LINES = 300;
    static final int MAX_BYTES = 64 * 1024;
    static final int MAX_PATH_LENGTH = 1024;
    static final int MAX_SEARCH_QUERY_LENGTH = 200;
    static final int MAX_GLOB_LENGTH = 256;
    static final int SEARCH_HARD_CAP = 100;
    static final int SEARCH_DEFAULT_MAX = 50;
    static final int MAX_SNIPPET_LENGTH = 200;
    static final long MAX_FILE_BYTES_TO_SCAN = 2L * 1024 * 1024;
    static final int MAX_FILES_TO_SCAN = 5_000;

    /** Extensions that may be served as text. Everything else is refused. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "xml", "properties", "yml", "yaml", "md", "txt", "sql", "kt", "gradle");

    /** Path segments that must never be traversed or served. */
    private static final Set<String> BLOCKED_DIR_SEGMENTS = Set.of(
            "target", "build", ".git", ".idea", "node_modules", "dist", "out", "bin", ".gradle");

    /** Filenames that are always refused regardless of extension. */
    private static final Set<String> BLOCKED_FILENAMES = Set.of(
            ".env", ".env.local", ".env.production", "id_rsa", "id_dsa", ".npmrc", ".netrc");

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(password|passwd|pwd)(\\s*[=:]\\s*)(\\S+)"),
            Pattern.compile("(?i)(secret|client[_-]?secret)(\\s*[=:]\\s*)(\\S+)"),
            Pattern.compile("(?i)(token|access[_-]?token|refresh[_-]?token)(\\s*[=:]\\s*)(\\S+)"),
            Pattern.compile("(?i)(api[_-]?key|apikey)(\\s*[=:]\\s*)(\\S+)"),
            Pattern.compile("(?i)(authorization)(\\s*[=:]\\s*)(\\S+)"));

    private static final Pattern AWS_ACCESS_KEY = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern PRIVATE_KEY_HEADER = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
    /** H15: match the whole PEM block, not just the header line — non-greedy so a file with
     * several keys does not swallow the legitimate content between them. */
    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");
    /** A BEGIN header whose END fell outside the served range — redact to the end of the range. */
    private static final Pattern PRIVATE_KEY_OPEN_ENDED = Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*$");
    /** An END footer whose BEGIN sits before the served range — redact the orphan body. */
    private static final Pattern PRIVATE_KEY_ORPHAN_END = Pattern.compile(
            "\\A[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");
    private static final String REDACTED = "[REDACTED]";

    private final ProjectService projectService;

    @Override
    public Path resolveProjectRoot(String projectId) {
        String normalized = requireText(projectId, "projectId", 512);
        String rootPath = projectService.getProject(normalized).getRootPath();
        if (rootPath == null || rootPath.isBlank()) {
            throw new IllegalStateException("Project has no source root registered: " + normalized);
        }
        try {
            return Path.of(rootPath).toRealPath();
        } catch (IOException | InvalidPathException ex) {
            throw new IllegalStateException("Project source root is not accessible");
        }
    }

    @Override
    public SourceContent readRange(String projectId, String rawPath, Integer startLine, Integer endLine) {
        Path root = resolveProjectRoot(projectId);
        String normalizedPath = requireText(rawPath, "filePathOrNodeId", MAX_PATH_LENGTH);

        Path candidate = resolveWithinRoot(root, normalizedPath);

        String relativePath = toRelative(root, candidate);
        String extension = extensionOf(candidate);

        if (isBlockedPath(root, candidate)) {
            return notServed(relativePath, "File type or location is not permitted for source reading.");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return notServed(relativePath, "File type ." + extension + " is not allowed for source reading.");
        }
        if (!Files.isRegularFile(candidate)) {
            return notServed(relativePath, "File not found in project source: " + relativePath);
        }
        if (isLikelyBinary(candidate)) {
            return notServed(relativePath, "File appears to be binary and is not served as source.");
        }

        // H14: cap the size BEFORE loading the file into RAM (same guard scanFile() uses).
        // A multi-GB text file must not be read whole just to serve the first 300 lines.
        try {
            if (Files.size(candidate) > MAX_FILE_BYTES_TO_SCAN) {
                return notServed(relativePath, "File too large for source reading.");
            }
        } catch (IOException ex) {
            return notServed(relativePath, "File could not be read as UTF-8 text.");
        }

        List<String> lines = readAllLines(candidate);
        if (lines == null) {
            return notServed(relativePath, "File could not be read as UTF-8 text.");
        }

        int totalLines = lines.size();
        List<String> warnings = new ArrayList<>();
        int start = clampStart(startLine, totalLines);
        int end = clampEnd(endLine, start, totalLines);

        boolean truncated = false;
        String truncationReason = null;
        if (end - start + 1 > MAX_LINES) {
            end = start + MAX_LINES - 1;
            truncated = true;
            truncationReason = "Line range capped at " + MAX_LINES + " lines.";
        }

        StringBuilder builder = new StringBuilder();
        int lastIncludedLine = start - 1;
        if (start <= end) {
            // H15: block-level redaction runs on the joined range text so the base64 body of a
            // private key disappears together with its BEGIN/END headers, not just the header line.
            String rangeText = String.join("\n", lines.subList(start - 1, end));
            for (String rangeLine : redactPrivateKeyBlocks(rangeText).split("\n", -1)) {
                String redacted = redact(rangeLine);
                if (builder.length() + redacted.length() + 1 > MAX_BYTES) {
                    truncated = true;
                    truncationReason = "Content capped at " + MAX_BYTES + " bytes.";
                    break;
                }
                builder.append(redacted).append('\n');
                lastIncludedLine++;
            }
        }
        if (lastIncludedLine < start) {
            lastIncludedLine = start;
        }

        return new SourceContent(
                true,
                relativePath,
                languageFor(extension),
                totalLines == 0 ? 0 : start,
                totalLines == 0 ? 0 : lastIncludedLine,
                totalLines,
                builder.toString(),
                truncated,
                truncationReason,
                warnings);
    }

    @Override
    public SearchOutcome search(String projectId, String query, String fileGlob, int maxResults) {
        Path root = resolveProjectRoot(projectId);
        String normalizedQuery = requireText(query, "query", MAX_SEARCH_QUERY_LENGTH);
        String needle = normalizedQuery.toLowerCase(Locale.ROOT);
        int cap = boundMaxResults(maxResults);
        PathMatcher matcher = compileGlob(fileGlob);

        List<SearchHit> hits = new ArrayList<>();
        int[] totalMatches = {0};
        int[] filesScanned = {0};
        List<String> warnings = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !isBlockedPath(root, path))
                    .filter(path -> ALLOWED_EXTENSIONS.contains(extensionOf(path)))
                    .filter(path -> matcher == null || matcher.matches(root.relativize(path)))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> {
                        if (filesScanned[0] >= MAX_FILES_TO_SCAN) {
                            return;
                        }
                        scanFile(root, path, needle, cap, hits, totalMatches, filesScanned);
                    });
        } catch (IOException | UncheckedIOException ex) {
            warnings.add("Search stopped early due to a filesystem error.");
        }

        boolean truncated = totalMatches[0] > hits.size();
        if (filesScanned[0] >= MAX_FILES_TO_SCAN) {
            warnings.add("Search scanned the first " + MAX_FILES_TO_SCAN + " files only.");
        }
        return new SearchOutcome(List.copyOf(hits), totalMatches[0], truncated, warnings);
    }

    private void scanFile(Path root, Path path, String needle, int cap,
                          List<SearchHit> hits, int[] totalMatches, int[] filesScanned) {
        try {
            if (Files.size(path) > MAX_FILE_BYTES_TO_SCAN || isLikelyBinary(path)) {
                return;
            }
        } catch (IOException ex) {
            return;
        }
        filesScanned[0]++;
        List<String> lines = readAllLines(path);
        if (lines == null) {
            return;
        }
        String relativePath = toRelative(root, path);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toLowerCase(Locale.ROOT).contains(needle)) {
                totalMatches[0]++;
                if (hits.size() < cap) {
                    hits.add(new SearchHit(relativePath, i + 1, snippet(lines.get(i))));
                }
            }
        }
    }

    // --- path resolution & validation ---------------------------------------------------------

    private Path resolveWithinRoot(Path root, String rawPath) {
        Path parsed;
        try {
            parsed = Path.of(rawPath);
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("filePathOrNodeId is not a valid path");
        }
        Path resolved = (parsed.isAbsolute() ? parsed : root.resolve(parsed)).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Resolved path escapes the project source root");
        }
        // Defense in depth: if the file exists, its real path must still be inside root
        // (guards against symlink escape).
        if (Files.exists(resolved)) {
            try {
                Path real = resolved.toRealPath();
                if (!real.startsWith(root)) {
                    throw new IllegalArgumentException("Resolved path escapes the project source root");
                }
                return real;
            } catch (IOException ex) {
                // fall through to the normalized (already-contained) path
            }
        }
        return resolved;
    }

    private boolean isBlockedPath(Path root, Path candidate) {
        Path relative = root.relativize(candidate);
        for (Path segment : relative) {
            if (BLOCKED_DIR_SEGMENTS.contains(segment.toString())) {
                return true;
            }
        }
        String fileName = candidate.getFileName() == null ? "" : candidate.getFileName().toString();
        return BLOCKED_FILENAMES.contains(fileName.toLowerCase(Locale.ROOT));
    }

    private PathMatcher compileGlob(String fileGlob) {
        if (fileGlob == null || fileGlob.isBlank()) {
            return null;
        }
        if (fileGlob.length() > MAX_GLOB_LENGTH) {
            throw new IllegalArgumentException("fileGlob is too long");
        }
        if (fileGlob.contains("..")) {
            throw new IllegalArgumentException("fileGlob must not contain path traversal");
        }
        if (fileGlob.contains("\u0000") || fileGlob.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("fileGlob must be printable");
        }
        try {
            return FileSystems.getDefault().getPathMatcher("glob:" + fileGlob);
        } catch (IllegalArgumentException | UnsupportedOperationException ex) {
            throw new IllegalArgumentException("fileGlob is not a valid glob pattern");
        }
    }

    // --- reading & redaction -------------------------------------------------------------------

    private List<String> readAllLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException ex) {
            return null;
        }
    }

    private boolean isLikelyBinary(Path path) {
        try {
            byte[] head = new byte[Math.min(8192, (int) Math.min(Files.size(path), 8192))];
            try (var in = Files.newInputStream(path)) {
                int read = in.read(head);
                for (int i = 0; i < read; i++) {
                    if (head[i] == 0) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException ex) {
            return true;
        }
    }

    /**
     * H15: redacts PEM private-key blocks over the joined range text rather than line by line,
     * so the base64 body between the BEGIN and END headers is removed along with the headers.
     * Also covers ranges that cut a block in half: an open BEGIN with the END beyond the range,
     * and an orphan END whose BEGIN sits before the range.
     */
    String redactPrivateKeyBlocks(String content) {
        String result = redactPreservingLineCount(PRIVATE_KEY_BLOCK, content);
        if (PRIVATE_KEY_HEADER.matcher(result).find()) {
            result = redactPreservingLineCount(PRIVATE_KEY_OPEN_ENDED, result);
        }
        if (PRIVATE_KEY_HEADER.matcher(result).find()) {
            return result;
        }
        return redactPreservingLineCount(PRIVATE_KEY_ORPHAN_END, result);
    }

    /**
     * Replaces every match with one {@code [REDACTED]} per line the match spanned, so the
     * redacted text has exactly as many lines as the input.
     *
     * <p>Collapsing a 28-line PEM block into a single token shortened the served text, and the
     * caller advances {@code lastIncludedLine} once per emitted line — so the reported
     * {@code endLine} came back short by however many lines the key occupied. MCP clients
     * paginate on that value, so an under-reported end line makes them re-read or skip a range.
     */
    private static String redactPreservingLineCount(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            int lines = (int) matcher.group().chars().filter(ch -> ch == '\n').count() + 1;
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    String.join("\n", Collections.nCopies(lines, REDACTED))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    String redact(String line) {
        String result = line;
        for (Pattern pattern : SECRET_PATTERNS) {
            Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                result = matcher.replaceAll("$1$2" + REDACTED);
            }
        }
        if (AWS_ACCESS_KEY.matcher(result).find()) {
            result = AWS_ACCESS_KEY.matcher(result).replaceAll(REDACTED);
        }
        if (PRIVATE_KEY_HEADER.matcher(result).find()) {
            result = REDACTED;
        }
        return result;
    }

    private String snippet(String line) {
        String trimmed = redact(line).strip();
        if (trimmed.length() > MAX_SNIPPET_LENGTH) {
            return trimmed.substring(0, MAX_SNIPPET_LENGTH) + "…";
        }
        return trimmed;
    }

    // --- small helpers -------------------------------------------------------------------------

    private SourceContent notServed(String relativePath, String warning) {
        List<String> warnings = new ArrayList<>();
        warnings.add(warning);
        return new SourceContent(false, relativePath, null, 0, 0, 0, "", false, null, warnings);
    }

    private int clampStart(Integer startLine, int totalLines) {
        if (startLine == null || startLine < 1) {
            return 1;
        }
        if (totalLines == 0) {
            return 1;
        }
        return Math.min(startLine, totalLines);
    }

    private int clampEnd(Integer endLine, int start, int totalLines) {
        if (totalLines == 0) {
            return 0;
        }
        if (endLine == null || endLine < start) {
            return Math.min(totalLines, start + MAX_LINES - 1);
        }
        return Math.min(endLine, totalLines);
    }

    private int boundMaxResults(int maxResults) {
        if (maxResults <= 0) {
            return SEARCH_DEFAULT_MAX;
        }
        return Math.min(maxResults, SEARCH_HARD_CAP);
    }

    private String toRelative(Path root, Path candidate) {
        try {
            return root.relativize(candidate).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return candidate.getFileName() == null ? "" : candidate.getFileName().toString();
        }
    }

    private String extensionOf(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String languageFor(String extension) {
        return switch (extension) {
            case "java" -> "java";
            case "kt" -> "kotlin";
            case "xml" -> "xml";
            case "properties" -> "properties";
            case "yml", "yaml" -> "yaml";
            case "md" -> "markdown";
            case "sql" -> "sql";
            case "gradle" -> "gradle";
            default -> "text";
        };
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    field + " must be non-blank, printable, and at most " + maxLength + " characters");
        }
        return value.trim();
    }
}
