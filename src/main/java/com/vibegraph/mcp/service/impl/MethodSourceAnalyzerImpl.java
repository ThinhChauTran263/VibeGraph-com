package com.vibegraph.mcp.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.MethodSourceContextResponse;
import com.vibegraph.mcp.dto.response.MethodSourceContextResponse.Candidate;
import com.vibegraph.mcp.dto.response.MethodSourceContextResponse.NodeRef;
import com.vibegraph.mcp.dto.response.MethodSourceContextResponse.RelatedSymbols;
import com.vibegraph.mcp.dto.response.MethodSourceContextResponse.ResolvedMethod;
import com.vibegraph.mcp.service.MethodSourceAnalyzer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.mcp.source.SourceFileService.SourceContent;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MethodSourceAnalyzerImpl implements MethodSourceAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_QUERY_LENGTH = 512;
    private static final int MAX_CANDIDATES = 20;
    private static final int MAX_RELATED = 25;
    private static final int FALLBACK_WINDOW = 80;
    private static final Set<String> METHOD_TYPES = Set.of("Method", "Constructor");
    private static final java.util.Comparator<NodeRef> NODE_REF_ORDER = java.util.Comparator
            .comparing((NodeRef ref) -> ref.getFullName() == null ? "" : ref.getFullName())
            .thenComparing(ref -> ref.getName() == null ? "" : ref.getName())
            .thenComparing(ref -> ref.getId() == null ? "" : ref.getId());

    private final SourceFileService sourceFileService;
    private final SourceGraphSupport graphSupport;

    @Override
    public MethodSourceContextResponse readMethodSource(String projectId, String methodQuery) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String normalizedQuery = validate(methodQuery, "methodQuery", MAX_QUERY_LENGTH);

        GraphView graph = safeLoad(normalizedProjectId);
        if (graph == null) {
            return warning(normalizedProjectId, normalizedQuery, "Method source is temporarily unavailable.");
        }

        GraphView.Resolution resolution = graph.resolve(normalizedQuery, METHOD_TYPES);
        if (resolution.kind() == GraphView.Resolution.Kind.NOT_FOUND) {
            return warning(normalizedProjectId, normalizedQuery, "Method not found: " + normalizedQuery);
        }
        if (resolution.kind() == GraphView.Resolution.Kind.AMBIGUOUS) {
            return ambiguous(normalizedProjectId, normalizedQuery, resolution.candidates());
        }

        NodeDto method = resolution.node();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        if (method.getFilePath() == null || method.getFilePath().isBlank() || method.getLineNumber() == null) {
            return warning(normalizedProjectId, normalizedQuery, "Method has no source location in the graph.");
        }

        int start = Math.max(1, method.getLineNumber());
        Integer endLine = SourceGraphSupport.endLineOf(method);
        int end;
        if (endLine == null) {
            end = start + FALLBACK_WINDOW;
            notes.add("End line is unknown in the graph; showing a bounded " + FALLBACK_WINDOW + "-line window from the declaration.");
        } else {
            end = Math.max(start, endLine);
        }

        SourceContent content;
        try {
            content = sourceFileService.readRange(normalizedProjectId, method.getFilePath(), start, end);
        } catch (ProjectNotFoundException | IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return warning(normalizedProjectId, normalizedQuery, "Method source is temporarily unavailable.");
        }
        warnings.addAll(content.warnings());

        return MethodSourceContextResponse.builder()
                .projectId(normalizedProjectId)
                .methodQuery(normalizedQuery)
                .resolvedMethod(toResolvedMethod(method, endLine))
                .relativePath(content.relativePath())
                .startLine(content.found() ? content.startLine() : null)
                .endLine(content.found() ? content.endLine() : null)
                .content(content.found() ? content.content() : null)
                .truncated(content.truncated())
                .truncationReason(content.truncationReason())
                .related(relatedSymbols(graph, method))
                .candidates(List.of())
                .warnings(warnings)
                .notes(notes)
                .build();
    }

    private RelatedSymbols relatedSymbols(GraphView graph, NodeDto method) {
        List<EdgeDto> outgoing = graph.outgoing(method.getId());
        List<EdgeDto> incoming = graph.incoming(method.getId());
        return RelatedSymbols.builder()
                .calls(refs(graph, outgoing, "CALLS", true))
                .calledBy(refs(graph, incoming, "CALLS", false))
                .reads(refs(graph, outgoing, "READS", true))
                .writes(refs(graph, outgoing, "WRITES", true))
                .returnsTypes(refs(graph, outgoing, "RETURNS", true))
                .build();
    }

    private List<NodeRef> refs(GraphView graph, List<EdgeDto> edges, String type, boolean useTarget) {
        return edges.stream()
                .filter(edge -> type.equals(edge.getType()))
                .map(edge -> graph.byId(useTarget ? edge.getTarget() : edge.getSource()))
                .filter(Objects::nonNull)
                .map(this::toNodeRef)
                .distinct()
                .sorted(NODE_REF_ORDER)
                .limit(MAX_RELATED)
                .toList();
    }

    private NodeRef toNodeRef(NodeDto node) {
        return NodeRef.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
                .build();
    }

    private ResolvedMethod toResolvedMethod(NodeDto method, Integer endLine) {
        String fullName = method.getFullName();
        String bare = GraphView.stripParens(fullName);
        String ownerClass = bare.contains(".") ? bare.substring(0, bare.lastIndexOf('.')) : null;
        String paramTypes = listProperty(method, "paramTypes");
        String returnType = SourceGraphSupport.stringProperty(method, "returnType");
        return ResolvedMethod.builder()
                .id(method.getId())
                .name(method.getName())
                .fullName(fullName)
                .ownerClass(ownerClass)
                .lineNumber(method.getLineNumber())
                .endLine(endLine)
                .returnType(returnType)
                .paramTypes(paramTypes)
                .signature(buildSignature(method.getName(), returnType, paramTypes))
                .visibility(SourceGraphSupport.stringProperty(method, "visibility"))
                .annotations(annotationsOf(method))
                .build();
    }

    /** Annotation simple names recorded by the parser on the method node (empty when absent). */
    private List<String> annotationsOf(NodeDto method) {
        if (method.getProperties() == null) {
            return List.of();
        }
        Object value = method.getProperties().get("annotations");
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String buildSignature(String name, String returnType, String paramTypes) {
        String params = paramTypes == null ? "" : paramTypes;
        String prefix = returnType == null || returnType.isBlank() ? "" : returnType + " ";
        return prefix + name + "(" + params + ")";
    }

    @SuppressWarnings("unchecked")
    private String listProperty(NodeDto node, String key) {
        if (node.getProperties() == null) {
            return null;
        }
        Object value = node.getProperties().get(key);
        if (value instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private MethodSourceContextResponse ambiguous(String projectId, String query, List<NodeDto> candidates) {
        List<Candidate> mapped = candidates.stream()
                .limit(MAX_CANDIDATES)
                .map(node -> Candidate.builder()
                        .id(node.getId())
                        .type(node.getType())
                        .name(node.getName())
                        .fullName(node.getFullName())
                        .build())
                .toList();
        return MethodSourceContextResponse.builder()
                .projectId(projectId)
                .methodQuery(query)
                .candidates(mapped)
                .warnings(List.of("Method query is ambiguous; refine using the full signature. Candidates: " + mapped.size()))
                .notes(List.of())
                .build();
    }

    private MethodSourceContextResponse warning(String projectId, String query, String message) {
        return MethodSourceContextResponse.builder()
                .projectId(projectId)
                .methodQuery(query)
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
}
