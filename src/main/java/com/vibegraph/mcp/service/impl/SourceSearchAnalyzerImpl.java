package com.vibegraph.mcp.service.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.SourceSearchResponse;
import com.vibegraph.mcp.dto.response.SourceSearchResponse.Match;
import com.vibegraph.mcp.service.SourceSearchAnalyzer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.mcp.source.SourceFileService.SearchHit;
import com.vibegraph.mcp.source.SourceFileService.SearchOutcome;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SourceSearchAnalyzerImpl implements SourceSearchAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_NODE_TYPE_LENGTH = 64;

    private final SourceFileService sourceFileService;
    private final SourceGraphSupport graphSupport;

    @Override
    public SourceSearchResponse searchSource(String projectId, String query, String fileGlob, String nodeType, Integer maxResults) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String normalizedNodeType = optional(nodeType, "nodeType", MAX_NODE_TYPE_LENGTH);
        int cap = maxResults == null ? 0 : maxResults;

        SearchOutcome outcome;
        try {
            outcome = sourceFileService.search(normalizedProjectId, query, fileGlob, cap);
        } catch (ProjectNotFoundException | IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return unavailable(normalizedProjectId, query, fileGlob);
        }

        List<String> warnings = new ArrayList<>(outcome.warnings());
        List<String> notes = new ArrayList<>();

        Map<String, List<NodeDto>> nodesByFile = nodesByRelativePath(normalizedProjectId, notes);
        List<Match> matches = new ArrayList<>();
        for (SearchHit hit : outcome.hits()) {
            NodeDto mapped = mapToNode(nodesByFile.get(hit.relativePath()), hit.lineNumber());
            if (normalizedNodeType != null && (mapped == null || !normalizedNodeType.equals(mapped.getType()))) {
                continue;
            }
            matches.add(Match.builder()
                    .relativePath(hit.relativePath())
                    .lineNumber(hit.lineNumber())
                    .snippet(hit.snippet())
                    .nodeId(mapped == null ? null : mapped.getId())
                    .nodeType(mapped == null ? null : mapped.getType())
                    .build());
        }

        return SourceSearchResponse.builder()
                .projectId(normalizedProjectId)
                .query(query == null ? null : query.trim())
                .fileGlob(fileGlob)
                .matches(matches)
                .totalMatches(outcome.totalMatches())
                .returnedMatches(matches.size())
                .truncated(outcome.truncated())
                .warnings(warnings)
                .notes(notes)
                .build();
    }

    private Map<String, List<NodeDto>> nodesByRelativePath(String projectId, List<String> notes) {
        GraphView graph;
        Path root;
        try {
            graph = graphSupport.load(projectId);
            root = sourceFileService.resolveProjectRoot(projectId);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return Map.of();
        }
        if (graph == null) {
            notes.add("Graph is unavailable or too large; matches are not mapped to symbols.");
            return Map.of();
        }
        Map<String, List<NodeDto>> byFile = new LinkedHashMap<>();
        for (NodeDto node : graph.nodes()) {
            if ("File".equals(node.getType()) || node.getFilePath() == null || node.getFilePath().isBlank()
                    || node.getLineNumber() == null) {
                continue;
            }
            String relative = relativize(root, node.getFilePath());
            if (relative != null) {
                byFile.computeIfAbsent(relative, key -> new ArrayList<>()).add(node);
            }
        }
        return byFile;
    }

    private NodeDto mapToNode(List<NodeDto> candidates, int lineNumber) {
        if (candidates == null) {
            return null;
        }
        NodeDto best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (NodeDto node : candidates) {
            int start = node.getLineNumber();
            Integer endLine = SourceGraphSupport.endLineOf(node);
            int end = endLine == null ? start : endLine;
            if (lineNumber >= start && lineNumber <= end) {
                int span = end - start;
                if (span < bestSpan) {
                    bestSpan = span;
                    best = node;
                }
            }
        }
        return best;
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

    private SourceSearchResponse unavailable(String projectId, String query, String fileGlob) {
        return SourceSearchResponse.builder()
                .projectId(projectId)
                .query(query == null ? null : query.trim())
                .fileGlob(fileGlob)
                .matches(List.of())
                .totalMatches(0)
                .returnedMatches(0)
                .truncated(false)
                .warnings(List.of("Source search is temporarily unavailable."))
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

    private String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must be printable and at most " + maxLength + " characters");
        }
        return value.trim();
    }
}
