package com.vibegraph.mcp.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.EdgeTypeEnum;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.ReferenceSearchResponse;
import com.vibegraph.mcp.dto.response.ReferenceSearchResponse.Candidate;
import com.vibegraph.mcp.dto.response.ReferenceSearchResponse.NodeRef;
import com.vibegraph.mcp.dto.response.ReferenceSearchResponse.Reference;
import com.vibegraph.mcp.dto.response.ReferenceSearchResponse.ResolvedSymbol;
import com.vibegraph.mcp.service.ReferenceAnalyzer;
import com.vibegraph.mcp.source.GraphView;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReferenceAnalyzerImpl implements ReferenceAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_QUERY_LENGTH = 512;
    private static final int MAX_RELATION_TYPES = 32;
    private static final int MAX_CANDIDATES = 20;
    private static final int DEFAULT_MAX = 50;
    private static final int HARD_CAP = 200;

    /** Whitelist of valid relationship types, sourced from the public edge-type contract. */
    private static final Set<String> VALID_EDGE_TYPES = Arrays.stream(EdgeTypeEnum.values())
            .map(EdgeTypeEnum::label)
            .collect(Collectors.toUnmodifiableSet());

    private final com.vibegraph.mcp.source.SourceGraphSupport graphSupport;

    @Override
    public ReferenceSearchResponse findReferences(
            String projectId, String symbolQuery, List<String> relationshipTypes, String direction, Integer maxResults) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String normalizedQuery = validate(symbolQuery, "symbolQuery", MAX_QUERY_LENGTH);
        Set<String> allowedTypes = validateRelationTypes(relationshipTypes);
        Direction dir = parseDirection(direction);
        int cap = boundMax(maxResults);

        GraphView graph = safeLoad(normalizedProjectId);
        if (graph == null) {
            return warning(normalizedProjectId, normalizedQuery, "References are temporarily unavailable.");
        }

        GraphView.Resolution resolution = graph.resolve(normalizedQuery, null);
        if (resolution.kind() == GraphView.Resolution.Kind.NOT_FOUND) {
            return warning(normalizedProjectId, normalizedQuery, "Symbol not found: " + normalizedQuery);
        }
        if (resolution.kind() == GraphView.Resolution.Kind.AMBIGUOUS) {
            return ambiguous(normalizedProjectId, normalizedQuery, resolution.candidates());
        }

        NodeDto symbol = resolution.node();
        List<Reference> references = new ArrayList<>();
        int total = 0;

        if (dir != Direction.INCOMING) {
            for (EdgeDto edge : graph.outgoing(symbol.getId())) {
                if (typeAllowed(edge, allowedTypes)) {
                    total++;
                    if (references.size() < cap) {
                        references.add(toReference(graph, edge, "OUTGOING"));
                    }
                }
            }
        }
        if (dir != Direction.OUTGOING) {
            for (EdgeDto edge : graph.incoming(symbol.getId())) {
                if (typeAllowed(edge, allowedTypes)) {
                    total++;
                    if (references.size() < cap) {
                        references.add(toReference(graph, edge, "INCOMING"));
                    }
                }
            }
        }

        return ReferenceSearchResponse.builder()
                .projectId(normalizedProjectId)
                .symbolQuery(normalizedQuery)
                .resolvedSymbol(ResolvedSymbol.builder()
                        .id(symbol.getId())
                        .type(symbol.getType())
                        .name(symbol.getName())
                        .fullName(symbol.getFullName())
                        .build())
                .references(references)
                .totalReferences(total)
                .returnedReferences(references.size())
                .truncated(total > references.size())
                .candidates(List.of())
                .warnings(List.of())
                .notes(List.of())
                .build();
    }

    private boolean typeAllowed(EdgeDto edge, Set<String> allowedTypes) {
        return allowedTypes.isEmpty() || allowedTypes.contains(edge.getType());
    }

    private Reference toReference(GraphView graph, EdgeDto edge, String direction) {
        return Reference.builder()
                .relationshipType(edge.getType())
                .direction(direction)
                .source(toNodeRef(graph.byId(edge.getSource()), edge.getSource()))
                .target(toNodeRef(graph.byId(edge.getTarget()), edge.getTarget()))
                .lineNumber(edge.getLineNumber())
                .build();
    }

    private NodeRef toNodeRef(NodeDto node, String fallbackId) {
        if (node == null) {
            return NodeRef.builder().id(fallbackId).build();
        }
        return NodeRef.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
                .build();
    }

    private Set<String> validateRelationTypes(List<String> relationshipTypes) {
        if (relationshipTypes == null || relationshipTypes.isEmpty()) {
            return Set.of();
        }
        if (relationshipTypes.size() > MAX_RELATION_TYPES) {
            throw new IllegalArgumentException("Too many relationship types requested");
        }
        Set<String> validated = new LinkedHashSet<>();
        for (String type : relationshipTypes) {
            if (type == null || type.isBlank()) {
                continue;
            }
            // EdgeTypeEnum is the public whitelist; unknown types are rejected loudly.
            String upper = type.trim().toUpperCase(Locale.ROOT);
            if (!VALID_EDGE_TYPES.contains(upper)) {
                throw new IllegalArgumentException("Unknown relationship type (not in graph schema): " + upper);
            }
            validated.add(upper);
        }
        return validated;
    }

    private Direction parseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return Direction.BOTH;
        }
        return switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "incoming", "in" -> Direction.INCOMING;
            case "outgoing", "out" -> Direction.OUTGOING;
            case "both" -> Direction.BOTH;
            default -> throw new IllegalArgumentException("direction must be incoming, outgoing, or both");
        };
    }

    private int boundMax(Integer maxResults) {
        if (maxResults == null || maxResults <= 0) {
            return DEFAULT_MAX;
        }
        return Math.min(maxResults, HARD_CAP);
    }

    private ReferenceSearchResponse ambiguous(String projectId, String query, List<NodeDto> candidates) {
        List<Candidate> mapped = candidates.stream()
                .limit(MAX_CANDIDATES)
                .map(node -> Candidate.builder()
                        .id(node.getId())
                        .type(node.getType())
                        .name(node.getName())
                        .fullName(node.getFullName())
                        .build())
                .toList();
        return ReferenceSearchResponse.builder()
                .projectId(projectId)
                .symbolQuery(query)
                .references(List.of())
                .totalReferences(0)
                .returnedReferences(0)
                .truncated(false)
                .candidates(mapped)
                .warnings(List.of("Symbol query is ambiguous; refine using the full name. Candidates: " + mapped.size()))
                .notes(List.of())
                .build();
    }

    private ReferenceSearchResponse warning(String projectId, String query, String message) {
        return ReferenceSearchResponse.builder()
                .projectId(projectId)
                .symbolQuery(query)
                .references(List.of())
                .totalReferences(0)
                .returnedReferences(0)
                .truncated(false)
                .candidates(List.of())
                .warnings(List.of(message))
                .notes(List.of())
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

    private String validate(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    field + " must be non-blank, printable, and at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private enum Direction { INCOMING, OUTGOING, BOTH }
}
