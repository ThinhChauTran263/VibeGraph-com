package com.vibegraph.graph.controller;

import com.vibegraph.graph.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Graph data REST controller.
 *
 * Endpoints:
 * - GET /api/projects/{id}/graph
 * - GET /api/projects/{id}/graph/nodes
 * - GET /api/projects/{id}/graph/neighbors/{nodeId}
 * - GET /api/projects/{id}/nodes/{nodeId}
 *
 * TODO: Implement endpoints
 */
@RestController
@RequestMapping("/api/projects/{projectId}/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    // TODO: Add endpoint methods
}
