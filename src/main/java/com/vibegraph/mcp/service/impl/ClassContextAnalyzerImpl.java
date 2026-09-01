package com.vibegraph.mcp.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.repository.ProjectMetadata;
import com.vibegraph.mcp.config.McpLimitProperties;
import com.vibegraph.mcp.dto.response.ClassContextResponse;
import com.vibegraph.mcp.service.ClassContextAnalyzer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceGraphSupport;

@Service
public class ClassContextAnalyzerImpl implements ClassContextAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_QUERY_LENGTH = 512;
    private static final int MAX_METHODS = 50;
    private static final int MAX_FIELDS = 50;
    private static final int MAX_RELATIONS = 50;
    private static final int MAX_CANDIDATES = 20;
    private static final Set<String> CLASS_NODE_TYPES = Set.of("Class", "Interface", "Enum");
    private static final Set<String> METHOD_EDGE_TYPES = Set.of("HAS_METHOD");
    private static final Set<String> FIELD_EDGE_TYPES = Set.of("HAS_FIELD", "HAS_FIELD_DECLARATION");

    private final GraphService graphService;
    private final McpLimitProperties limits;

    /** Compatibility constructor for isolated unit tests and direct callers. */
    public ClassContextAnalyzerImpl(GraphService graphService) {
        this(graphService, new McpLimitProperties());
    }

    @Autowired
    public ClassContextAnalyzerImpl(GraphService graphService, McpLimitProperties limits) {
        this.graphService = graphService;
        this.limits = limits;
    }

    @Override
    public ClassContextResponse analyzeClass(String projectId, String classQuery) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String normalizedQuery = validate(classQuery, "classQuery", MAX_QUERY_LENGTH);
        ProjectMetadata metadata = graphService.getProjectMetadata(normalizedProjectId);
        if (metadata != null && (metadata.totalNodes() > limits.getMaxNodes()
                || metadata.totalEdges() > limits.getMaxEdges())) {
            return tooLargeResponse(normalizedProjectId, normalizedQuery,
                    metadata.totalNodes(), metadata.totalEdges());
        }
        GraphDataResponse graphData;
        try {
            graphData = graphService.getFullGraph(normalizedProjectId);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return unavailableResponse(normalizedProjectId, normalizedQuery);
        }
        int nodeCount = graphData == null || graphData.getNodes() == null ? 0 : graphData.getNodes().size();
        int edgeCount = graphData == null || graphData.getEdges() == null ? 0 : graphData.getEdges().size();
        // Use the shared MCP bounds so sibling graph-reading tools accept the same graph sizes.
        if (nodeCount > limits.getMaxNodes() || edgeCount > limits.getMaxEdges()) {
            return tooLargeResponse(normalizedProjectId, normalizedQuery, nodeCount, edgeCount);
        }
        GraphView graph = new GraphView(
                graphData == null ? null : graphData.getNodes(),
                graphData == null ? null : graphData.getEdges());

        GraphView.Resolution resolution = graph.resolve(normalizedQuery, CLASS_NODE_TYPES);
        if (resolution.kind() == GraphView.Resolution.Kind.NOT_FOUND) {
            return emptyResponse(normalizedProjectId, normalizedQuery,
                    List.of("Class not found: " + normalizedQuery), List.of());
        }
        if (resolution.kind() == GraphView.Resolution.Kind.AMBIGUOUS) {
            return ambiguousResponse(normalizedProjectId, normalizedQuery, resolution.candidates());
        }

        NodeDto classNode = resolution.node();
        List<EdgeDto> outgoingEdges = graph.outgoing(classNode.getId());
        List<EdgeDto> incomingEdges = graph.incoming(classNode.getId());
        List<ClassContextResponse.MemberInfo> methods = relatedMembers(graph, outgoingEdges, METHOD_EDGE_TYPES, MAX_METHODS);
        List<ClassContextResponse.MemberInfo> fields = relatedMembers(graph, outgoingEdges, FIELD_EDGE_TYPES, MAX_FIELDS);
        List<ClassContextResponse.RelationInfo> incoming = relations(graph, incomingEdges, MAX_RELATIONS);
        List<ClassContextResponse.RelationInfo> outgoing = relations(graph, outgoingEdges, MAX_RELATIONS);
        List<String> warnings = warnings(methods, fields, incoming, outgoing, outgoingEdges, incomingEdges);

        return ClassContextResponse.builder()
                .projectId(normalizedProjectId)
                .query(normalizedQuery)
                .classInfo(toClassInfo(classNode))
                .methods(methods)
                .fields(fields)
                .incomingRelations(incoming)
                .outgoingRelations(outgoing)
                .candidates(List.of())
                .warnings(warnings)
                .build();
    }

    private ClassContextResponse unavailableResponse(String projectId, String query) {
        return emptyResponse(projectId, query, List.of("Class context is temporarily unavailable."), List.of());
    }

    private ClassContextResponse tooLargeResponse(String projectId, String query, int nodeCount, int edgeCount) {
        return emptyResponse(projectId, query,
                List.of("Graph is too large for class context: " + nodeCount + " nodes, " + edgeCount + " edges."),
                List.of());
    }

    private ClassContextResponse ambiguousResponse(String projectId, String query, List<NodeDto> candidates) {
        List<ClassContextResponse.Candidate> mapped = candidates.stream()
                .limit(MAX_CANDIDATES)
                .map(node -> ClassContextResponse.Candidate.builder()
                        .id(node.getId())
                        .type(node.getType())
                        .name(node.getName())
                        .fullName(node.getFullName())
                        .build())
                .toList();
        return emptyResponse(projectId, query,
                List.of("Class query is ambiguous; refine using the full name. Candidates: " + mapped.size()),
                mapped);
    }

    private ClassContextResponse emptyResponse(String projectId, String query, List<String> warnings,
            List<ClassContextResponse.Candidate> candidates) {
        return ClassContextResponse.builder()
                .projectId(projectId)
                .query(query)
                .methods(List.of())
                .fields(List.of())
                .incomingRelations(List.of())
                .outgoingRelations(List.of())
                .candidates(candidates)
                .warnings(warnings)
                .build();
    }

    private String validate(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength || hasControlCharacter(value)) {
            throw new IllegalArgumentException(fieldName + " must be non-blank, printable, and at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private List<ClassContextResponse.MemberInfo> relatedMembers(
            GraphView graph,
            List<EdgeDto> outgoingEdges,
            Set<String> edgeTypes,
            int limit) {
        return outgoingEdges.stream()
                .filter(edge -> edgeTypes.contains(edge.getType()))
                .map(edge -> graph.byId(edge.getTarget()))
                .filter(Objects::nonNull)
                .map(this::toMemberInfo)
                .distinct()
                .sorted(memberComparator())
                .limit(limit)
                .toList();
    }

    private List<ClassContextResponse.RelationInfo> relations(GraphView graph, List<EdgeDto> edges, int limit) {
        return edges.stream()
                .map(edge -> toRelationInfo(graph, edge))
                .filter(Objects::nonNull)
                .sorted(relationComparator())
                .limit(limit)
                .toList();
    }

    private ClassContextResponse.ClassInfo toClassInfo(NodeDto node) {
        return ClassContextResponse.ClassInfo.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
                .layer(stringProperty(node, "springLayer"))
                .lineNumber(node.getLineNumber())
                .build();
    }

    private ClassContextResponse.MemberInfo toMemberInfo(NodeDto node) {
        return ClassContextResponse.MemberInfo.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
                .signature(stringProperty(node, "signature"))
                .visibility(stringProperty(node, "visibility"))
                .lineNumber(node.getLineNumber())
                .build();
    }

    private ClassContextResponse.RelationInfo toRelationInfo(GraphView graph, EdgeDto edge) {
        NodeDto source = graph.byId(edge.getSource());
        NodeDto target = graph.byId(edge.getTarget());
        if (source == null || target == null) {
            return null;
        }
        return ClassContextResponse.RelationInfo.builder()
                .id(SourceGraphSupport.relativizePath(edge.getId()))
                .type(edge.getType())
                .source(toNodeRef(source))
                .target(toNodeRef(target))
                .confidence(edge.getConfidence())
                .lineNumber(edge.getLineNumber())
                .build();
    }

    private ClassContextResponse.NodeRef toNodeRef(NodeDto node) {
        return ClassContextResponse.NodeRef.builder()
                .id(SourceGraphSupport.relativizePath(node.getId()))
                .type(node.getType())
                .name(node.getName())
                .fullName(SourceGraphSupport.relativizePath(node.getFullName()))
                .build();
    }

    private String stringProperty(NodeDto node, String key) {
        if (node.getProperties() == null) {
            return null;
        }
        Object value = node.getProperties().get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private List<String> warnings(
            List<ClassContextResponse.MemberInfo> methods,
            List<ClassContextResponse.MemberInfo> fields,
            List<ClassContextResponse.RelationInfo> incoming,
            List<ClassContextResponse.RelationInfo> outgoing,
            List<EdgeDto> outgoingEdges,
            List<EdgeDto> incomingEdges) {
        List<String> warnings = new ArrayList<>();
        addLimitWarning(warnings, "methods", methods.size(), countByType(outgoingEdges, METHOD_EDGE_TYPES));
        addLimitWarning(warnings, "fields", fields.size(), countByType(outgoingEdges, FIELD_EDGE_TYPES));
        addLimitWarning(warnings, "incomingRelations", incoming.size(), incomingEdges.size());
        addLimitWarning(warnings, "outgoingRelations", outgoing.size(), outgoingEdges.size());
        return warnings;
    }

    private void addLimitWarning(List<String> warnings, String label, int returnedCount, long totalCount) {
        if (totalCount > returnedCount) {
            warnings.add(label + " truncated to " + returnedCount + " of " + totalCount);
        }
    }

    private long countByType(List<EdgeDto> edges, Set<String> edgeTypes) {
        return edges.stream().filter(edge -> edgeTypes.contains(edge.getType())).count();
    }

    private Comparator<ClassContextResponse.MemberInfo> memberComparator() {
        return Comparator.comparing((ClassContextResponse.MemberInfo member) -> safeString(member.getName()))
                .thenComparing(member -> safeString(member.getSignature()))
                .thenComparing(member -> safeString(member.getId()));
    }

    private Comparator<ClassContextResponse.RelationInfo> relationComparator() {
        return Comparator.comparing((ClassContextResponse.RelationInfo relation) -> safeString(relation.getType()))
                .thenComparing(relation -> safeString(relation.getSource().getFullName()))
                .thenComparing(relation -> safeString(relation.getTarget().getFullName()))
                .thenComparing(relation -> safeString(relation.getId()));
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
