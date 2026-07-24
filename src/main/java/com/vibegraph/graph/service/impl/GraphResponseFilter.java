package com.vibegraph.graph.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vibegraph.graph.dto.request.GraphFilterRequest;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.repository.impl.neo4j.GraphSchema;

/**
 * Applies bounded graph filters at the HTTP boundary without changing repository facts.
 */
@Component
public class GraphResponseFilter {

    public GraphDataResponse apply(GraphDataResponse graph, GraphFilterRequest request) {
        if (graph == null || request == null || request.isEmpty()) {
            return graph;
        }

        List<NodeDto> sourceNodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        List<EdgeDto> sourceEdges = graph.getEdges() == null ? List.of() : graph.getEdges();
        Set<String> nodeTypes = normalizeNodeTypes(request.effectiveNodeTypes());
        Set<String> edgeTypes = normalizeEdgeTypes(request.getEdgeTypes());
        String packagePath = request.effectivePackagePath();

        Map<String, NodeDto> nodesById = new LinkedHashMap<>();
        for (NodeDto node : sourceNodes) {
            if (node != null && node.getId() != null) {
                nodesById.put(node.getId(), node);
            }
        }

        Set<String> keptNodeIds = packagePath == null || packagePath.isBlank()
                ? new LinkedHashSet<>(nodesById.keySet())
                : packageMatchedNodeIds(sourceNodes, packagePath.trim());

        if (request.getMaxDepth() != null && request.getMaxDepth() > 0
                && packagePath != null && !packagePath.isBlank()) {
            keptNodeIds = expandByDepth(keptNodeIds, sourceEdges, request.getMaxDepth());
        }
        final Set<String> finalKeptNodeIds = keptNodeIds;

        List<NodeDto> nodes = sourceNodes.stream()
                .filter(node -> node != null && finalKeptNodeIds.contains(node.getId()))
                .filter(node -> nodeTypes.isEmpty() || nodeTypes.contains(node.getType()))
                .toList();
        Set<String> finalNodeIds = nodes.stream()
                .map(NodeDto::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<EdgeDto> edges = sourceEdges.stream()
                .filter(edge -> edge != null)
                .filter(edge -> edgeTypes.isEmpty() || edgeTypes.contains(edge.getType()))
                .filter(edge -> finalNodeIds.contains(edge.getSource()) && finalNodeIds.contains(edge.getTarget()))
                .toList();

        return GraphDataResponse.builder()
                .nodes(nodes)
                .edges(edges)
                .nodeStats(nodeStats(nodes))
                .edgeStats(edgeStats(edges))
                .build();
    }

    private Set<String> packageMatchedNodeIds(List<NodeDto> nodes, String packagePath) {
        Set<String> ids = new LinkedHashSet<>();
        for (NodeDto node : nodes) {
            if (node == null || node.getId() == null) {
                continue;
            }
            Object packageName = node.getProperties() == null ? null : node.getProperties().get("packageName");
            if (packageName instanceof String value && value.startsWith(packagePath)) {
                ids.add(node.getId());
            } else if ("Package".equals(node.getType())
                    && node.getFullName() != null
                    && node.getFullName().startsWith(packagePath)) {
                ids.add(node.getId());
            }
        }
        return ids;
    }

    private Set<String> expandByDepth(Set<String> seeds, List<EdgeDto> edges, int maxDepth) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (EdgeDto edge : edges) {
            if (edge == null || edge.getSource() == null || edge.getTarget() == null) {
                continue;
            }
            adjacency.computeIfAbsent(edge.getSource(), ignored -> new LinkedHashSet<>()).add(edge.getTarget());
            adjacency.computeIfAbsent(edge.getTarget(), ignored -> new LinkedHashSet<>()).add(edge.getSource());
        }

        Set<String> visited = new LinkedHashSet<>(seeds);
        ArrayDeque<NodeDepth> queue = new ArrayDeque<>();
        seeds.forEach(seed -> queue.add(new NodeDepth(seed, 0)));
        while (!queue.isEmpty()) {
            NodeDepth current = queue.removeFirst();
            if (current.depth() >= maxDepth) {
                continue;
            }
            for (String next : adjacency.getOrDefault(current.nodeId(), Set.of())) {
                if (visited.add(next)) {
                    queue.addLast(new NodeDepth(next, current.depth() + 1));
                }
            }
        }
        return visited;
    }

    private Set<String> normalizeNodeTypes(List<String> rawTypes) {
        Set<String> result = new LinkedHashSet<>();
        for (String type : flatten(rawTypes)) {
            GraphSchema.nodeLabel(type);
            result.add(type);
        }
        return result;
    }

    private Set<String> normalizeEdgeTypes(List<String> rawTypes) {
        Set<String> result = new LinkedHashSet<>();
        for (String type : flatten(rawTypes)) {
            String normalized = type.toUpperCase(Locale.ROOT);
            result.add(GraphSchema.relationshipType(normalized));
        }
        return result;
    }

    private List<String> flatten(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private Map<String, Integer> nodeStats(List<NodeDto> nodes) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (NodeDto node : nodes) {
            if (node != null && node.getType() != null) {
                stats.merge(node.getType(), 1, Integer::sum);
            }
        }
        return stats;
    }

    private Map<String, Integer> edgeStats(List<EdgeDto> edges) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (EdgeDto edge : edges) {
            if (edge != null && edge.getType() != null) {
                stats.merge(edge.getType(), 1, Integer::sum);
            }
        }
        return stats;
    }

    private record NodeDepth(String nodeId, int depth) {
    }
}
