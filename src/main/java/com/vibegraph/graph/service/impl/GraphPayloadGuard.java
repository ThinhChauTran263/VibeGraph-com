package com.vibegraph.graph.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * Caps an HTTP full-graph payload to bounded node/edge counts and attaches truncation metadata.
 *
 * <p>This is the server-side safety layer that prevents the browser from ever receiving an
 * unbounded graph. It mirrors the deterministic selection used by the frontend
 * {@code lib/graphCap.ts} so both layers agree on which nodes are "most important":
 * <ol>
 *   <li>node-type priority (Project/Package/File/Class first, Field/LocalVariable last);</li>
 *   <li>then node degree (more connected, more central nodes win);</li>
 *   <li>then id (deterministic, stable output — never random).</li>
 * </ol>
 * Edges whose source or target node did not survive are dropped; the surviving edges are then
 * capped (deterministically by id) to the edge limit.
 */
@Component
public class GraphPayloadGuard {

    private static final String REASON_TOO_LARGE = "GRAPH_TOO_LARGE";

    /** Higher = kept first. Keep in sync with the frontend lib/graphCap.ts tiers. */
    private static final Map<String, Integer> NODE_TYPE_PRIORITY = Map.ofEntries(
            Map.entry("Project", 100),
            Map.entry("Package", 90),
            Map.entry("File", 80),
            Map.entry("Class", 70),
            Map.entry("Interface", 70),
            Map.entry("Enum", 65),
            Map.entry("Record", 65),
            Map.entry("DBModel", 65),
            Map.entry("Route", 60),
            Map.entry("APIEndpoint", 60),
            Map.entry("Method", 40),
            Map.entry("Constructor", 40),
            Map.entry("Field", 20),
            Map.entry("Annotation", 15),
            Map.entry("External", 10),
            Map.entry("LocalVariable", 5));

    /**
     * Cap the given graph to at most {@code nodeLimit} nodes and {@code edgeLimit} edges, attaching
     * {@link GraphDataResponse.Meta}. The input is never mutated. A null/empty graph is returned
     * with a non-truncated meta so the frontend always has truthful counts.
     */
    public GraphDataResponse cap(GraphDataResponse graph, int nodeLimit, int edgeLimit) {
        List<NodeDto> nodes = graph != null && graph.getNodes() != null ? graph.getNodes() : List.of();
        List<EdgeDto> edges = graph != null && graph.getEdges() != null ? graph.getEdges() : List.of();
        int totalNodes = nodes.size();
        int totalEdges = edges.size();

        boolean nodesOver = nodeLimit > 0 && totalNodes > nodeLimit;
        boolean edgesOver = edgeLimit > 0 && totalEdges > edgeLimit;

        if (!nodesOver && !edgesOver) {
            return rebuild(graph, nodes, edges,
                    meta(false, totalNodes, totalEdges, totalNodes, totalEdges, nodeLimit, edgeLimit, null));
        }

        // 1. Degree from the full edge set drives centrality within a type tier.
        Map<String, Integer> degree = new HashMap<>();
        for (EdgeDto edge : edges) {
            if (edge == null) {
                continue;
            }
            if (edge.getSource() != null) {
                degree.merge(edge.getSource(), 1, Integer::sum);
            }
            if (edge.getTarget() != null) {
                degree.merge(edge.getTarget(), 1, Integer::sum);
            }
        }

        // 2. Deterministic node selection.
        List<NodeDto> keptNodes = nodes;
        if (nodesOver) {
            List<NodeDto> ranked = new ArrayList<>(nodes);
            ranked.sort(Comparator
                    .comparingInt((NodeDto n) -> typePriority(n.getType())).reversed()
                    .thenComparing(Comparator.comparingInt((NodeDto n) -> degree.getOrDefault(n.getId(), 0)).reversed())
                    .thenComparing(n -> n.getId() == null ? "" : n.getId()));
            keptNodes = new ArrayList<>(ranked.subList(0, nodeLimit));
        }

        Set<String> keptIds = new HashSet<>();
        for (NodeDto node : keptNodes) {
            if (node != null && node.getId() != null) {
                keptIds.add(node.getId());
            }
        }

        // 3. Drop edges with a missing endpoint, then deterministically cap to the edge limit.
        List<EdgeDto> connectedEdges = new ArrayList<>();
        for (EdgeDto edge : edges) {
            if (edge != null && keptIds.contains(edge.getSource()) && keptIds.contains(edge.getTarget())) {
                connectedEdges.add(edge);
            }
        }
        List<EdgeDto> keptEdges = connectedEdges;
        if (edgeLimit > 0 && connectedEdges.size() > edgeLimit) {
            connectedEdges.sort(Comparator.comparing(e -> e.getId() == null ? "" : e.getId()));
            keptEdges = new ArrayList<>(connectedEdges.subList(0, edgeLimit));
        }

        boolean truncated = keptNodes.size() < totalNodes || keptEdges.size() < totalEdges;
        return rebuild(graph, keptNodes, keptEdges,
                meta(truncated, totalNodes, totalEdges, keptNodes.size(), keptEdges.size(),
                        nodeLimit, edgeLimit, truncated ? REASON_TOO_LARGE : null));
    }

    private int typePriority(String type) {
        if (type == null) {
            return 0;
        }
        return NODE_TYPE_PRIORITY.getOrDefault(type, 0);
    }

    private GraphDataResponse rebuild(GraphDataResponse src, List<NodeDto> nodes, List<EdgeDto> edges,
            GraphDataResponse.Meta meta) {
        return GraphDataResponse.builder()
                .nodes(nodes)
                .edges(edges)
                // Stats reflect the FULL graph (legend totals), so they are preserved as-is.
                .nodeStats(src == null ? null : src.getNodeStats())
                .edgeStats(src == null ? null : src.getEdgeStats())
                .meta(meta)
                .build();
    }

    private GraphDataResponse.Meta meta(boolean truncated, int totalNodes, int totalEdges,
            int returnedNodes, int returnedEdges, int nodeLimit, int edgeLimit, String reason) {
        return GraphDataResponse.Meta.builder()
                .truncated(truncated)
                .totalNodes(totalNodes)
                .totalEdges(totalEdges)
                .returnedNodes(returnedNodes)
                .returnedEdges(returnedEdges)
                .nodeLimit(nodeLimit)
                .edgeLimit(edgeLimit)
                .reason(reason)
                .build();
    }
}
