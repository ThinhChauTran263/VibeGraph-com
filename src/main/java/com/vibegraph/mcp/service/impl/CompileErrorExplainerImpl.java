package com.vibegraph.mcp.service.impl;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.CompileErrorExplanationResponse;
import com.vibegraph.mcp.dto.response.CompileErrorExplanationResponse.CompileError;
import com.vibegraph.mcp.dto.response.CompileErrorExplanationResponse.SymbolRef;
import com.vibegraph.mcp.service.CompileErrorExplainer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

/**
 * Parses javac / Maven compiler output ("[ERROR] /path/File.java:[12,34] message" and
 * "File.java:12: error: message") and maps each error to the enclosing graph symbol with
 * actionable hints — vibe-coding dies most often inside the compile-fix loop.
 */
@Service
@RequiredArgsConstructor
public class CompileErrorExplainerImpl implements CompileErrorExplainer {

    private static final int DEFAULT_MAX_ERRORS = 20;
    private static final int HARD_CAP_ERRORS = 50;
    private static final int MAX_OUTPUT_LENGTH = 200_000;
    private static final Set<String> METHODLIKE = Set.of("Method", "Constructor");

    private static final Pattern MAVEN_ERROR =
            Pattern.compile("(?m)^\\s*\\[ERROR\\]\\s+(.+?\\.java):\\[(\\d+)(?:,\\d+)?\\]\\s+(.+)$");
    private static final Pattern JAVAC_ERROR =
            Pattern.compile("(?m)^\\s*(.+?\\.java):(\\d+):\\s*(?:error|warning):\\s*(.+)$");
    private static final Pattern MISSING_SYMBOL =
            Pattern.compile("symbol:\\s*(?:class|interface|variable|method)?\\s*([A-Za-z_$][\\w$]*)");
    private static final Pattern MISSING_PACKAGE = Pattern.compile("package\\s+([\\w.]+)\\s+does not exist");

    private final SourceGraphSupport graphSupport;
    private final SourceFileService sourceFileService;

    @Override
    public CompileErrorExplanationResponse explainCompileError(String projectId, String compilerOutput, Integer maxErrors) {
        String normalizedProjectId = validate(projectId, "projectId", 512);
        if (compilerOutput == null || compilerOutput.isBlank()) {
            throw new IllegalArgumentException("compilerOutput must be non-blank");
        }
        String output = compilerOutput.length() > MAX_OUTPUT_LENGTH
                ? compilerOutput.substring(0, MAX_OUTPUT_LENGTH)
                : compilerOutput;
        int cap = maxErrors == null || maxErrors <= 0 ? DEFAULT_MAX_ERRORS : Math.min(maxErrors, HARD_CAP_ERRORS);

        List<ParsedError> parsed = parse(output, cap);
        List<String> warnings = new ArrayList<>();
        if (parsed.isEmpty()) {
            warnings.add("No compiler errors could be parsed. Expected '[ERROR] File.java:[line,col] message' "
                    + "(Maven) or 'File.java:line: error: message' (javac) lines.");
        }

        GraphView graph = safeLoad(normalizedProjectId);
        Path root = safeRoot(normalizedProjectId);
        if (graph == null) {
            warnings.add("Graph is unavailable; errors are reported without symbol mapping.");
        }

        List<CompileError> errors = new ArrayList<>();
        for (ParsedError error : parsed) {
            errors.add(explainOne(graph, root, error));
        }

        return CompileErrorExplanationResponse.builder()
                .projectId(normalizedProjectId)
                .parsedErrors(parsed.size())
                .errors(errors)
                .warnings(warnings)
                .notes(List.of(
                        "Fix errors top-down: later errors are often cascades of the first one.",
                        "After fixing a symbol with callers, run verify_change with the touched files."))
                .build();
    }

    private CompileError explainOne(GraphView graph, Path root, ParsedError error) {
        String relativePath = relativize(root, error.path);
        NodeDto enclosing = graph == null ? null : enclosingSymbol(graph, root, error);
        Integer callersCount = null;
        if (enclosing != null && METHODLIKE.contains(enclosing.getType())) {
            int callers = 0;
            for (EdgeDto edge : graph.incoming(enclosing.getId())) {
                if ("CALLS".equals(edge.getType())) {
                    callers++;
                }
            }
            callersCount = callers;
        }
        return CompileError.builder()
                .relativePath(relativePath != null ? relativePath : fileNameOf(error.path))
                .lineNumber(error.line)
                .message(error.message)
                .symbol(enclosing == null ? null : SymbolRef.builder()
                        .id(enclosing.getId())
                        .type(enclosing.getType())
                        .name(enclosing.getName())
                        .fullName(enclosing.getFullName())
                        .build())
                .callersCount(callersCount)
                .hints(hints(error.message, enclosing, callersCount))
                .build();
    }

    /** Smallest-span symbol containing the error line in the reported file. */
    private NodeDto enclosingSymbol(GraphView graph, Path root, ParsedError error) {
        String absolute = absolutePath(root, error.path);
        if (absolute == null) {
            return null;
        }
        NodeDto best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (NodeDto node : graph.nodesInFile(absolute)) {
            Integer start = node.getLineNumber();
            if (start == null) {
                continue;
            }
            Integer endLine = SourceGraphSupport.endLineOf(node);
            int end = endLine == null ? start : endLine;
            if (error.line >= start && error.line <= end && end - start < bestSpan) {
                bestSpan = end - start;
                best = node;
            }
        }
        return best;
    }

    private List<String> hints(String message, NodeDto enclosing, Integer callersCount) {
        List<String> hints = new ArrayList<>(new LinkedHashSet<>(rawHints(message)));
        if (callersCount != null && callersCount > 0) {
            hints.add(callersCount + " caller(s) reach "
                    + (enclosing == null ? "this method" : enclosing.getName())
                    + " - a signature fix must be mirrored at the call sites (get_impact_analysis shows them).");
        }
        return hints;
    }

    private List<String> rawHints(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        List<String> hints = new ArrayList<>();
        if (lower.contains("cannot find symbol")) {
            Matcher symbol = MISSING_SYMBOL.matcher(message);
            String name = symbol.find() ? symbol.group(1) : null;
            hints.add("Missing symbol" + (name == null ? "" : " '" + name + "'")
                    + " - check imports and spelling; locate candidates with search_source"
                    + (name == null ? "" : "('" + name + "')") + " or get_class_context.");
        }
        Matcher pkg = MISSING_PACKAGE.matcher(message);
        if (pkg.find()) {
            hints.add("Package " + pkg.group(1) + " is not on the classpath - check pom.xml dependencies "
                    + "and the import statement.");
        }
        if (lower.contains("incompatible types")) {
            hints.add("Type mismatch - compare declared vs actual types; get_method_cpg_context shows "
                    + "parameter and return types of the involved methods.");
        }
        if (lower.contains("does not override or implement")) {
            hints.add("@Override target is gone - the supertype signature changed; run get_impact_analysis "
                    + "on the supertype method to find every implementation to update.");
        }
        if (lower.contains("unreported exception")) {
            hints.add("Checked exception must be caught or declared - get_method_cpg_context lists the "
                    + "THROWS edges of the called method.");
        }
        if (lower.contains("is not abstract and does not override")) {
            hints.add("A new abstract/interface method is unimplemented - get_class_context on the supertype "
                    + "lists the required methods.");
        }
        if (hints.isEmpty()) {
            hints.add("Open the reported line with get_source_file to see the failing statement in context.");
        }
        return hints;
    }

    private List<ParsedError> parse(String output, int cap) {
        List<ParsedError> errors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collect(MAVEN_ERROR.matcher(output), errors, seen, cap);
        collect(JAVAC_ERROR.matcher(output), errors, seen, cap);
        return errors;
    }

    private void collect(Matcher matcher, List<ParsedError> errors, Set<String> seen, int cap) {
        while (matcher.find() && errors.size() < cap) {
            String path = matcher.group(1).trim();
            int line = Integer.parseInt(matcher.group(2));
            String message = matcher.group(3).trim();
            if (seen.add(path + ":" + line + ":" + message)) {
                errors.add(new ParsedError(path, line, message));
            }
        }
    }

    private String absolutePath(Path root, String reportedPath) {
        try {
            Path parsed = Path.of(reportedPath.replace('/', java.io.File.separatorChar));
            Path absolute = parsed.isAbsolute() ? parsed.normalize()
                    : root == null ? null : root.resolve(parsed).normalize();
            if (absolute == null || (root != null && !absolute.startsWith(root))) {
                return null;
            }
            return absolute.toString();
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    private String relativize(Path root, String reportedPath) {
        if (root == null) {
            return null;
        }
        String absolute = absolutePath(root, reportedPath);
        if (absolute == null) {
            return null;
        }
        try {
            return root.relativize(Path.of(absolute)).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String fileNameOf(String reportedPath) {
        String normalized = reportedPath.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private Path safeRoot(String projectId) {
        try {
            return sourceFileService.resolveProjectRoot(projectId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private GraphView safeLoad(String projectId) {
        try {
            return graphSupport.load(projectId);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String validate(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    field + " must be non-blank, printable, and at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private record ParsedError(String path, int line, String message) {
    }
}
