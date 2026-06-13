package com.vibegraph.mcp.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.mcp.dto.response.ClassContextResponse;
import com.vibegraph.mcp.service.ClassContextAnalyzer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassContextAnalyzerImpl implements ClassContextAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_QUERY_LENGTH = 512;
    private static final int MAX_METHODS = 50;
    private static final int MAX_FIELDS = 50;
    private static final int MAX_RELATIONS = 50;
    private static final int MAX_NODES_TO_PROCESS = 10_000;
    private static final int MAX_EDGES_TO_PROCESS = 50_000;
    private static final List<String> CLASS_NODE_TYPES = List.of("Class", "Interface", "Enum");
    private static final List<String> METHOD_EDGE_TYPES = List.of("HAS_METHOD");
    private static final List<String> FIELD_EDGE_TYPES = List.of("HAS_FIELD", "HAS_FIELD_DECLARATION");

    private final GraphService graphService;

    @Override
    public ClassContextResponse analyzeClass(String projectId, String classQuery) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String normalizedQuery = validate(classQuery, "classQuery", MAX_QUERY_LENGTH);
        GraphDataResponse graph;
        try {
            graph = graphService.getFullGraph(normalizedProjectId);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return unavailableResponse(normalizedProjectId, normalizedQuery);
        }
        List<NodeDto> nodes = safeNodes(graph);
        List<EdgeDto> edges = safeEdges(graph);
        if (nodes.size() > MAX_NODES_TO_PROCESS || edges.size() > MAX_EDGES_TO_PROCESS) {
            return tooLargeResponse(normalizedProjectId, normalizedQuery, nodes.size(), edges.size());
        }
        Map<String, NodeDto> nodesById = indexNodes(nodes);
        Optional<NodeDto> matchedClass = findClass(nodes, normalizedQuery);

        if (matchedClass.isEmpty()) {
            return ClassContextResponse.builder()
                    .projectId(normalizedProjectId)
                    .query(normalizedQuery)
                    .methods(List.of())
                    .fields(List.of())
                    .incomingRelations(List.of())
                    .outgoingRelations(List.of())
                    .warnings(List.of("Class not found: " + normalizedQuery))
                    .build();
        }

        NodeDto classNode = matchedClass.get();
        List<ClassContextResponse.MemberInfo> methods = relatedMembers(classNode, edges, nodesById, METHOD_EDGE_TYPES, MAX_METHODS);
        List<ClassContextResponse.MemberInfo> fields = relatedMembers(classNode, edges, nodesById, FIELD_EDGE_TYPES, MAX_FIELDS);
        List<ClassContextResponse.RelationInfo> incoming = relations(classNode, edges, nodesById, edge -> classNode.getId().equals(edge.getTarget()), MAX_RELATIONS);
        List<ClassContextResponse.RelationInfo> outgoing = relations(classNode, edges, nodesById, edge -> classNode.getId().equals(edge.getSource()), MAX_RELATIONS);
        List<String> warnings = warnings(methods, fields, incoming, outgoing, classNode, edges);

        return ClassContextResponse.builder()
                .projectId(normalizedProjectId)
                .query(normalizedQuery)
                .classInfo(toClassInfo(classNode))
                .methods(methods)
                .fields(fields)
                .incomingRelations(incoming)
                .outgoingRelations(outgoing)
                .warnings(warnings)
                .build();
    }

    private ClassContextResponse unavailableResponse(String projectId, String query) {
        return ClassContextResponse.builder()
                .projectId(projectId)
                .query(query)
                .methods(List.of())
                .fields(List.of())
                .incomingRelations(List.of())
                .outgoingRelations(List.of())
                .warnings(List.of("Class context is temporarily unavailable."))
                .build();
    }

    private ClassContextResponse tooLargeResponse(String projectId, String query, int nodeCount, int edgeCount) {
        return ClassContextResponse.builder()
                .projectId(projectId)
                .query(query)
                .methods(List.of())
                .fields(List.of())
                .incomingRelations(List.of())
                .outgoingRelations(List.of())
                .warnings(List.of("Graph is too large for class context: " + nodeCount + " nodes, " + edgeCount + " edges."))
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

    private List<NodeDto> safeNodes(GraphDataResponse graph) {
        if (graph == null || graph.getNodes() == null) {
            return List.of();
        }
        return graph.getNodes().stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private List<EdgeDto> safeEdges(GraphDataResponse graph) {
        if (graph == null || graph.getEdges() == null) {
            return List.of();
        }
        return graph.getEdges().stream()
                .filter(Objects::nonNull)
                .filter(edge -> edge.getSource() != null && edge.getTarget() != null)
                .toList();
    }

    private Map<String, NodeDto> indexNodes(List<NodeDto> nodes) {
        Map<String, NodeDto> nodesById = new LinkedHashMap<>();
        nodes.stream()
                .filter(node -> node.getId() != null && !node.getId().isBlank())
                .sorted(nodeComparator())
                .forEach(node -> nodesById.putIfAbsent(node.getId(), node));
        return nodesById;
    }

    private Optional<NodeDto> findClass(List<NodeDto> nodes, String query) {
        return nodes.stream()
                .filter(this::isClassNode)
                .filter(node -> query.equals(node.getId()) || query.equals(node.getFullName()) || query.equals(node.getName()))
                .sorted(nodeComparator())
                .findFirst();
    }

    private boolean isClassNode(NodeDto node) {
        return CLASS_NODE_TYPES.contains(node.getType());
    }

    private List<ClassContextResponse.MemberInfo> relatedMembers(
            NodeDto classNode,
            List<EdgeDto> edges,
            Map<String, NodeDto> nodesById,
            List<String> edgeTypes,
            int limit) {
        return edges.stream()
                .filter(edge -> classNode.getId().equals(edge.getSource()))
                .filter(edge -> edgeTypes.contains(edge.getType()))
                .map(edge -> nodesById.get(edge.getTarget()))
                .filter(Objects::nonNull)
                .map(this::toMemberInfo)
                .distinct()
                .sorted(memberComparator())
                .limit(limit)
                .toList();
    }

    private List<ClassContextResponse.RelationInfo> relations(
            NodeDto classNode,
            List<EdgeDto> edges,
            Map<String, NodeDto> nodesById,
            Predicate<EdgeDto> predicate,
            int limit) {
        return edges.stream()
                .filter(predicate)
                .map(edge -> toRelationInfo(edge, nodesById))
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

    private ClassContextResponse.RelationInfo toRelationInfo(EdgeDto edge, Map<String, NodeDto> nodesById) {
        NodeDto source = nodesById.get(edge.getSource());
        NodeDto target = nodesById.get(edge.getTarget());
        if (source == null || target == null) {
            return null;
        }
        return ClassContextResponse.RelationInfo.builder()
                .id(edge.getId())
                .type(edge.getType())
                .source(toNodeRef(source))
                .target(toNodeRef(target))
                .confidence(edge.getConfidence())
                .lineNumber(edge.getLineNumber())
                .build();
    }

    private ClassContextResponse.NodeRef toNodeRef(NodeDto node) {
        return ClassContextResponse.NodeRef.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
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
            NodeDto classNode,
            List<EdgeDto> edges) {
        List<String> warnings = new ArrayList<>();
        addLimitWarning(warnings, "methods", methods.size(), countRelated(edges, classNode, METHOD_EDGE_TYPES));
        addLimitWarning(warnings, "fields", fields.size(), countRelated(edges, classNode, FIELD_EDGE_TYPES));
        addLimitWarning(warnings, "incomingRelations", incoming.size(), countRelations(edges, edge -> classNode.getId().equals(edge.getTarget())));
        addLimitWarning(warnings, "outgoingRelations", outgoing.size(), countRelations(edges, edge -> classNode.getId().equals(edge.getSource())));
        return warnings;
    }

    private void addLimitWarning(List<String> warnings, String label, int returnedCount, long totalCount) {
        if (totalCount > returnedCount) {
            warnings.add(label + " truncated to " + returnedCount + " of " + totalCount);
        }
    }

    private long countRelated(List<EdgeDto> edges, NodeDto classNode, List<String> edgeTypes) {
        return edges.stream()
                .filter(edge -> classNode.getId().equals(edge.getSource()))
                .filter(edge -> edgeTypes.contains(edge.getType()))
                .count();
    }

    private long countRelations(List<EdgeDto> edges, Predicate<EdgeDto> predicate) {
        return edges.stream().filter(predicate).count();
    }

    private Comparator<NodeDto> nodeComparator() {
        return Comparator.comparing((NodeDto node) -> safeString(node.getFullName()))
                .thenComparing(node -> safeString(node.getName()))
                .thenComparing(node -> safeString(node.getId()));
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
