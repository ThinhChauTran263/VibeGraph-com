package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.vibegraph.diagram.dto.response.UseCaseResponse;
import com.vibegraph.diagram.service.MermaidGeneratorService;
import com.vibegraph.diagram.service.UseCaseDiagramService;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@inheritDoc}
 *
 * <p>Reads the project graph through {@link GraphService#getFullGraph(String)}
 * (no direct Neo4j access) and derives the use case diagram from Route nodes
 * and {@code HANDLES_ROUTE} edges.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UseCaseDiagramServiceImpl implements UseCaseDiagramService {

    private static final String ROUTE_NODE_TYPE = "Route";
    private static final String HANDLES_ROUTE_EDGE = "HANDLES_ROUTE";
    private static final String ACTOR_HTTP_CLIENT = "HTTP Client";
    private static final String NL = "\n";
    private static final String INDENT = "    ";

    private final GraphService graphService;
    private final MermaidGeneratorService mermaid;

    @Override
    public UseCaseResponse generateUseCaseDiagram(String projectId) {
        GraphDataResponse graph = graphService.getFullGraph(projectId);

        List<NodeDto> nodes = graph != null && graph.getNodes() != null ? graph.getNodes() : List.of();
        List<EdgeDto> edges = graph != null && graph.getEdges() != null ? graph.getEdges() : List.of();

        Map<String, NodeDto> nodesById = new HashMap<>();
        for (NodeDto node : nodes) {
            if (node != null && node.getId() != null) {
                nodesById.put(node.getId(), node);
            }
        }

        // Deterministic ordering: TreeMap keyed by the stable route identifier.
        // Value is the display label shown in the diagram.
        Map<String, String> useCasesByRoute = new TreeMap<>();

        // 1) Routes reachable via a handler method (the common case).
        for (EdgeDto edge : edges) {
            if (edge == null || !HANDLES_ROUTE_EDGE.equals(edge.getType())) {
                continue;
            }
            String routeId = edge.getTarget();
            if (routeId == null || routeId.isBlank()) {
                continue;
            }
            useCasesByRoute.putIfAbsent(routeId, routeLabel(nodesById.get(routeId), routeId));
        }

        // 2) Orphan Route nodes with no HANDLES_ROUTE edge — still represent a
        //    reachable endpoint, so include them rather than silently dropping.
        for (NodeDto node : nodes) {
            if (node == null || !ROUTE_NODE_TYPE.equals(node.getType()) || node.getId() == null) {
                continue;
            }
            useCasesByRoute.putIfAbsent(node.getId(), routeLabel(node, node.getId()));
        }

        // NOTE: job/listener actors (@Scheduled, @KafkaListener, @EventListener)
        // are not yet represented in the graph data model — the parser only emits
        // Route nodes + HANDLES_ROUTE edges. They are skipped here until that data
        // exists (see T39 note in the sprint doc).

        List<String> useCaseLabels = new ArrayList<>(useCasesByRoute.values());
        List<String> actors = useCaseLabels.isEmpty() ? List.of() : List.of(ACTOR_HTTP_CLIENT);

        String syntax = buildMermaid(useCasesByRoute);

        return UseCaseResponse.builder()
                .actors(actors)
                .useCases(useCaseLabels)
                .mermaidSyntax(syntax)
                .build();
    }

    private String routeLabel(NodeDto routeNode, String fallback) {
        if (routeNode != null && routeNode.getName() != null && !routeNode.getName().isBlank()) {
            return routeNode.getName();
        }
        return fallback;
    }

    private String buildMermaid(Map<String, String> useCasesByRoute) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR").append(NL);

        if (useCasesByRoute.isEmpty()) {
            sb.append(INDENT).append("%% No use cases detected for this project");
            return sb.toString();
        }

        String actorId = "actor_" + mermaid.sanitizeId(ACTOR_HTTP_CLIENT);
        sb.append(INDENT)
                .append(actorId)
                .append("((\"")
                .append(mermaid.escapeLabel(ACTOR_HTTP_CLIENT))
                .append("\"))")
                .append(NL);

        Set<String> usedIds = new HashSet<>();
        usedIds.add(actorId);

        // First pass: declare use case nodes (deterministic by route id).
        // Second pass: declare edges. Keep node ids aligned across both passes.
        Map<String, String> idByRoute = new TreeMap<>();
        for (Map.Entry<String, String> entry : useCasesByRoute.entrySet()) {
            String useCaseId = uniqueId("uc_" + mermaid.sanitizeId(entry.getKey()), usedIds);
            idByRoute.put(entry.getKey(), useCaseId);
            sb.append(INDENT)
                    .append(useCaseId)
                    .append("[\"")
                    .append(mermaid.escapeLabel(entry.getValue()))
                    .append("\"]")
                    .append(NL);
        }

        for (Map.Entry<String, String> entry : idByRoute.entrySet()) {
            sb.append(INDENT)
                    .append(actorId)
                    .append(" --> ")
                    .append(entry.getValue())
                    .append(NL);
        }

        // Drop the trailing newline for a clean, stable output.
        if (sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private String uniqueId(String base, Set<String> used) {
        if (used.add(base)) {
            return base;
        }
        int suffix = 2;
        String candidate = base + "_" + suffix;
        while (!used.add(candidate)) {
            suffix++;
            candidate = base + "_" + suffix;
        }
        return candidate;
    }
}
