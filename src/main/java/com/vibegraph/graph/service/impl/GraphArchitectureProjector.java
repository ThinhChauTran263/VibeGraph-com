package com.vibegraph.graph.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * Builds the default human-readable architecture graph from the persisted deep graph.
 *
 * <p>The persisted graph may contain detail nodes and relationships needed for code intelligence
 * (fields, local variables, type signatures, annotations). This projector does not delete those
 * facts; it returns the smaller architecture contract used by the main graph UI.
 */
@Component
public class GraphArchitectureProjector {

    private static final Set<String> ARCHITECTURE_NODE_TYPES = Set.of(
            "File", "Class", "Interface", "Enum", "Record", "DBModel",
            "Method", "Constructor", "APIEndpoint");
    private static final Set<String> ARCHITECTURE_EDGE_TYPES = Set.of(
            "DEFINES", "HAS_METHOD", "HAS_INNER", "HAS_RELATION", "IMPORTS", "CALLS", "INJECTS",
            "HANDLES_ROUTE", "EXTENDS", "IMPLEMENTS", "OVERRIDES", "STEP_IN_FLOW",
            "RESOLVES_TO", "TRIGGERS");
    private static final Set<String> FILE_DEFINED_NODE_TYPES = Set.of(
            "Class", "Interface", "Enum", "Record", "DBModel");
    private static final Set<String> FILE_DEPENDENCY_EDGE_TYPES = Set.of(
            "IMPORTS", "CALLS", "INJECTS", "EXTENDS", "IMPLEMENTS", "OVERRIDES",
            "HAS_RELATION", "STEP_IN_FLOW", "RESOLVES_TO", "TRIGGERS");

    public GraphDataResponse project(GraphDataResponse graph) {
        List<NodeDto> sourceNodes = graph == null || graph.getNodes() == null ? List.of() : graph.getNodes();
        List<EdgeDto> sourceEdges = graph == null || graph.getEdges() == null ? List.of() : graph.getEdges();

        List<NodeDto> nodes = new ArrayList<>();
        Map<String, NodeDto> keptById = new HashMap<>();
        Map<String, Integer> nodeStats = new HashMap<>();
        for (NodeDto node : sourceNodes) {
            if (node == null || !ARCHITECTURE_NODE_TYPES.contains(node.getType())) {
                continue;
            }
            nodes.add(node);
            if (node.getId() != null) {
                keptById.put(node.getId(), node);
            }
            nodeStats.merge(node.getType(), 1, Integer::sum);
        }

        Map<String, EdgeAggregate> edgesByKey = new LinkedHashMap<>();
        for (EdgeDto edge : sourceEdges) {
            if (edge == null || !ARCHITECTURE_EDGE_TYPES.contains(edge.getType())) {
                continue;
            }
            NodeDto source = keptById.get(edge.getSource());
            NodeDto target = keptById.get(edge.getTarget());
            if (source == null || target == null || !isArchitectureEdge(edge, source, target)) {
                continue;
            }
            putEdge(edgesByKey, edge);
        }

        addFileDependencyProjection(edgesByKey, sourceEdges, keptById);

        List<EdgeDto> edges = edgesByKey.values().stream()
                .map(EdgeAggregate::toEdge)
                .toList();
        Map<String, Integer> edgeStats = new HashMap<>();
        for (EdgeDto edge : edges) {
            edgeStats.merge(edge.getType(), 1, Integer::sum);
        }

        return GraphDataResponse.builder()
                .nodes(nodes)
                .edges(edges)
                .nodeStats(nodeStats)
                .edgeStats(edgeStats)
                .build();
    }

    private void addFileDependencyProjection(Map<String, EdgeAggregate> edgesByKey,
            List<EdgeDto> sourceEdges, Map<String, NodeDto> keptById) {
        Map<String, String> fileNodeByPath = new HashMap<>();
        for (NodeDto node : keptById.values()) {
            if ("File".equals(node.getType()) && node.getFilePath() != null && !node.getFilePath().isBlank()) {
                fileNodeByPath.put(node.getFilePath(), node.getId());
            }
        }
        if (fileNodeByPath.isEmpty()) {
            return;
        }

        for (EdgeDto edge : sourceEdges) {
            if (edge == null || !FILE_DEPENDENCY_EDGE_TYPES.contains(edge.getType())) {
                continue;
            }
            NodeDto source = keptById.get(edge.getSource());
            NodeDto target = keptById.get(edge.getTarget());
            if (source == null || target == null) {
                continue;
            }
            String sourceFile = fileNodeByPath.get(source.getFilePath());
            String targetFile = fileNodeByPath.get(target.getFilePath());
            if (sourceFile == null || targetFile == null || sourceFile.equals(targetFile)) {
                continue;
            }
            putEdge(edgesByKey, EdgeDto.builder()
                    .id(stableEdgeId(sourceFile, edge.getType(), targetFile))
                    .source(sourceFile)
                    .target(targetFile)
                    .type(edge.getType())
                    .lineNumber(edge.getLineNumber())
                    .weight(edge.getWeight())
                    .occurrences(edge.getOccurrences())
                    .properties(edge.getProperties())
                    .build());
        }
    }

    private boolean isArchitectureEdge(EdgeDto edge, NodeDto source, NodeDto target) {
        if (!"DEFINES".equals(edge.getType())) {
            return true;
        }
        return "File".equals(source.getType()) && FILE_DEFINED_NODE_TYPES.contains(target.getType());
    }

    private void putEdge(Map<String, EdgeAggregate> edgesByKey, EdgeDto edge) {
        String key = stableEdgeId(edge.getSource(), edge.getType(), edge.getTarget());
        edgesByKey.computeIfAbsent(key, ignored -> new EdgeAggregate(edge)).add(edge);
    }

    private String stableEdgeId(String source, String type, String target) {
        return source + "|" + type + "|" + target;
    }

    private static final class EdgeAggregate {
        private final String id;
        private final String source;
        private final String target;
        private final String type;
        private final Double confidence;
        private final Map<String, Object> properties;
        private final List<Integer> occurrences = new ArrayList<>();
        private int weight;
        private Integer lineNumber;

        private EdgeAggregate(EdgeDto first) {
            this.id = first.getId();
            this.source = first.getSource();
            this.target = first.getTarget();
            this.type = first.getType();
            this.confidence = first.getConfidence();
            this.properties = first.getProperties();
        }

        private void add(EdgeDto edge) {
            weight += edge.getWeight() == null ? 1 : Math.max(edge.getWeight(), 1);
            if (lineNumber == null && edge.getLineNumber() != null) {
                lineNumber = edge.getLineNumber();
            }
            if (edge.getOccurrences() != null) {
                for (Integer occurrence : edge.getOccurrences()) {
                    if (occurrences.size() >= 10) {
                        break;
                    }
                    if (occurrence != null) {
                        occurrences.add(occurrence);
                    }
                }
            } else if (edge.getLineNumber() != null && occurrences.size() < 10) {
                occurrences.add(edge.getLineNumber());
            }
        }

        private EdgeDto toEdge() {
            return EdgeDto.builder()
                    .id(id)
                    .source(source)
                    .target(target)
                    .type(type)
                    .confidence(confidence)
                    .lineNumber(lineNumber)
                    .weight(weight)
                    .occurrences(occurrences.isEmpty() ? null : occurrences)
                    .properties(properties)
                    .build();
        }
    }
}
