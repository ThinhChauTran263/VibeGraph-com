package com.vibegraph.graph.controller;

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
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.impl.GraphArchitectureProjector;
import com.vibegraph.graph.service.impl.GraphPayloadGuard;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectId}/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final GraphArchitectureProjector architectureProjector;
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
            return architectureProjector.project(graph);
        }
        throw new IllegalArgumentException("mode must be baseline or deep");
    }

    private boolean isDeepMode(String mode) {
        return mode != null && "deep".equalsIgnoreCase(mode.trim());
    }

    private boolean isBaselineMode(String mode) {
        return mode != null && "baseline".equalsIgnoreCase(mode.trim());
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
