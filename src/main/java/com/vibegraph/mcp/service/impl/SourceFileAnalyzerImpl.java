package com.vibegraph.mcp.service.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.SourceFileContextResponse;
import com.vibegraph.mcp.dto.response.SourceFileContextResponse.SymbolInfo;
import com.vibegraph.mcp.service.SourceFileAnalyzer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.mcp.source.SourceFileService.SourceContent;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SourceFileAnalyzerImpl implements SourceFileAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_QUERY_LENGTH = 1024;
    private static final int MAX_SYMBOLS = 100;
    private static final List<String> METHOD_TYPES = List.of("Method", "Constructor");

    private final SourceFileService sourceFileService;
    private final SourceGraphSupport graphSupport;

    @Override
    public SourceFileContextResponse readSourceFile(String projectId, String filePathOrNodeId, Integer startLine, Integer endLine) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String normalizedQuery = validate(filePathOrNodeId, "filePathOrNodeId", MAX_QUERY_LENGTH);

        GraphView graph = safeLoad(normalizedProjectId);
        List<String> notes = new ArrayList<>();

        NodeDto matchedNode = resolveNode(graph, normalizedQuery);
        String targetPath = normalizedQuery;
        String nodeId = null;
        if (matchedNode != null && matchedNode.getFilePath() != null && !matchedNode.getFilePath().isBlank()) {
            targetPath = matchedNode.getFilePath();
            nodeId = matchedNode.getId();
            if (METHOD_TYPES.contains(matchedNode.getType())) {
                notes.add("Matched a method node; use get_method_source for the exact method body.");
            }
        }

        SourceContent content;
        try {
            content = sourceFileService.readRange(normalizedProjectId, targetPath, startLine, endLine);
        } catch (ProjectNotFoundException | IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return unavailable(normalizedProjectId, normalizedQuery);
        }

        List<String> warnings = new ArrayList<>(content.warnings());
        List<SymbolInfo> symbols = List.of();
        if (content.found() && graph != null) {
            symbols = symbolsForFile(normalizedProjectId, graph, content.relativePath());
        }

        return SourceFileContextResponse.builder()
                .projectId(normalizedProjectId)
                .query(normalizedQuery)
                .nodeId(nodeId)
                .relativePath(content.relativePath())
                .language(content.language())
                .startLine(content.found() ? content.startLine() : null)
                .endLine(content.found() ? content.endLine() : null)
                .totalLines(content.found() ? content.totalLines() : null)
                .content(content.found() ? content.content() : null)
                .truncated(content.truncated())
                .truncationReason(content.truncationReason())
                .symbols(symbols)
                .warnings(warnings)
                .notes(notes)
                .build();
    }

    private NodeDto resolveNode(GraphView graph, String query) {
        if (graph == null) {
            return null;
        }
        GraphView.Resolution resolution = graph.resolve(query, null);
        return resolution.isUnique() ? resolution.node() : null;
    }

    private List<SymbolInfo> symbolsForFile(String projectId, GraphView graph, String relativePath) {
        Path root;
        try {
            root = sourceFileService.resolveProjectRoot(projectId);
        } catch (RuntimeException ex) {
            return List.of();
        }
        return graph.nodes().stream()
                .filter(node -> !"File".equals(node.getType()))
                .filter(node -> node.getFilePath() != null && !node.getFilePath().isBlank())
                .filter(node -> relativePath.equals(relativize(root, node.getFilePath())))
                .sorted(Comparator.comparing(node -> node.getLineNumber() == null ? Integer.MAX_VALUE : node.getLineNumber()))
                .limit(MAX_SYMBOLS)
                .map(this::toSymbolInfo)
                .toList();
    }

    private String relativize(Path root, String absoluteFilePath) {
        try {
            Path filePath = Path.of(absoluteFilePath).normalize();
            if (!filePath.startsWith(root)) {
                return null;
            }
            return root.relativize(filePath).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            // Covers InvalidPathException (a subclass) and relativize() mismatch.
            return null;
        }
    }

    private SymbolInfo toSymbolInfo(NodeDto node) {
        return SymbolInfo.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
                .lineNumber(node.getLineNumber())
                .endLine(SourceGraphSupport.endLineOf(node))
                .build();
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

    private SourceFileContextResponse unavailable(String projectId, String query) {
        return SourceFileContextResponse.builder()
                .projectId(projectId)
                .query(query)
                .symbols(List.of())
                .warnings(List.of("Source file reading is temporarily unavailable."))
                .notes(List.of())
                .build();
    }

    private String validate(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    field + " must be non-blank, printable, and at most " + maxLength + " characters");
        }
        return value.trim();
    }
}
