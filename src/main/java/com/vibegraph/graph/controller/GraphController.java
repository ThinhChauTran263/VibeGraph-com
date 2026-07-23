package com.vibegraph.graph.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.graph.config.GraphPayloadProperties;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.impl.GraphPayloadGuard;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectId}/graph")
@RequiredArgsConstructor
public class GraphController {

    private static final Set<String> BASELINE_NODE_TYPES = Set.of(
            "Project", "Package", "File", "Class", "Interface", "Enum", "Record", "DBModel",
            "Method", "Constructor", "APIEndpoint");
    private static final Set<String> BASELINE_EDGE_TYPES = Set.of(
            "CONTAINS", "DEFINES", "HAS_METHOD", "HAS_INNER", "IMPORTS", "CALLS", "INJECTS",
            "HANDLES_ROUTE", "EXTENDS", "IMPLEMENTS", "OVERRIDES", "STEP_IN_FLOW");

    private final GraphService graphService;
    private final GraphPayloadGuard payloadGuard;
    private final GraphPayloadProperties payloadProperties;
    private final ProjectOwnershipGuard ownershipGuard;

    /**
     * Full project graph, capped at the HTTP boundary so the browser never receives an
     * unbounded payload. Defaults come from {@link GraphPayloadProperties}; callers may request
     * higher explicit limits via {@code nodeLimit}/{@code edgeLimit}, clamped to the configured
     * server maximums. The response carries {@code meta} describing any truncation.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<GraphDataResponse>> getFullGraph(
            @PathVariable String projectId,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "false") boolean includeDeep,
            @RequestParam(required = false) Integer nodeLimit,
            @RequestParam(required = false) Integer edgeLimit) {
        ownershipGuard.assertOwner(projectId);
        int effectiveNodeLimit = clamp(nodeLimit, payloadProperties.getNodeLimit(),
                payloadProperties.getMaxNodeLimit());
        int effectiveEdgeLimit = clamp(edgeLimit, payloadProperties.getEdgeLimit(),
                payloadProperties.getMaxEdgeLimit());
        GraphDataResponse full = graphService.getFullGraph(projectId);
        GraphDataResponse view = selectView(full, mode, includeDeep);
        GraphDataResponse capped = payloadGuard.cap(view, effectiveNodeLimit, effectiveEdgeLimit);
        return ResponseEntity.ok(ApiResponse.success(capped));
    }

    /**
     * Clamp a requested limit to {@code [1, max]}, falling back to {@code defaultValue} when the
     * caller did not request one. A non-positive request also falls back to the default.
     */
    private int clamp(Integer requested, int defaultValue, int max) {
        if (requested == null || requested <= 0) {
            return Math.min(defaultValue, max);
        }
        return Math.min(requested, max);
    }

    private GraphDataResponse selectView(GraphDataResponse graph, String mode, boolean includeDeep) {
        if (includeDeep || isDeepMode(mode)) {
            return graph;
        }
        if (mode == null || mode.isBlank() || isBaselineMode(mode)) {
            return baselineView(graph);
        }
        throw new IllegalArgumentException("mode must be baseline or deep");
    }

    private boolean isDeepMode(String mode) {
        return mode != null && "deep".equalsIgnoreCase(mode.trim());
    }

    private boolean isBaselineMode(String mode) {
        return mode != null && "baseline".equalsIgnoreCase(mode.trim());
    }

    private GraphDataResponse baselineView(GraphDataResponse graph) {
        List<NodeDto> sourceNodes = graph == null || graph.getNodes() == null ? List.of() : graph.getNodes();
        List<EdgeDto> sourceEdges = graph == null || graph.getEdges() == null ? List.of() : graph.getEdges();

        List<NodeDto> nodes = new ArrayList<>();
        Set<String> keptIds = new HashSet<>();
        Map<String, Integer> nodeStats = new HashMap<>();
        for (NodeDto node : sourceNodes) {
            if (node == null || !BASELINE_NODE_TYPES.contains(node.getType())) {
                continue;
            }
            nodes.add(node);
            if (node.getId() != null) {
                keptIds.add(node.getId());
            }
            nodeStats.merge(node.getType(), 1, Integer::sum);
        }

        List<EdgeDto> edges = new ArrayList<>();
        Map<String, Integer> edgeStats = new HashMap<>();
        for (EdgeDto edge : sourceEdges) {
            if (edge == null || !BASELINE_EDGE_TYPES.contains(edge.getType())) {
                continue;
            }
            if (!keptIds.contains(edge.getSource()) || !keptIds.contains(edge.getTarget())) {
                continue;
            }
            edges.add(edge);
            edgeStats.merge(edge.getType(), 1, Integer::sum);
        }

        return GraphDataResponse.builder()
                .nodes(nodes)
                .edges(edges)
                .nodeStats(nodeStats)
                .edgeStats(edgeStats)
                .build();
    }

    @GetMapping("/impact")
    public ResponseEntity<ApiResponse<ImpactAnalysisResponse>> getImpactAnalysis(
            @PathVariable String projectId,
            @RequestParam String nodeId,
            @RequestParam(defaultValue = "3") int depth,
            @RequestParam(defaultValue = "dependency") String profile) {
        ownershipGuard.assertOwner(projectId);
        ImpactProfile impactProfile = ImpactProfile.fromApiValue(profile);
        return ResponseEntity.ok(ApiResponse.success(graphService.getImpactAnalysis(projectId, nodeId, depth, impactProfile)));
    }

    @GetMapping("/neighbors/{nodeId}")
    public ResponseEntity<ApiResponse<NodeDetailResponse>> getNodeDetail(
            @PathVariable String projectId,
            @PathVariable String nodeId,
            @RequestParam(defaultValue = "1") int hops) {
        ownershipGuard.assertOwner(projectId);
        return ResponseEntity.ok(ApiResponse.success(graphService.getNodeDetail(projectId, nodeId, hops)));
    }

    @GetMapping("/neighbors")
    public ResponseEntity<ApiResponse<NodeDetailResponse>> getNodeDetailByQuery(
            @PathVariable String projectId,
            @RequestParam String nodeId,
            @RequestParam(defaultValue = "1") int hops) {
        ownershipGuard.assertOwner(projectId);
        return ResponseEntity.ok(ApiResponse.success(graphService.getNodeDetail(projectId, nodeId, hops)));
    }
}
