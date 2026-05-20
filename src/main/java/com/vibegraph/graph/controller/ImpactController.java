package com.vibegraph.graph.controller;

import com.vibegraph.graph.service.ImpactService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Impact analysis REST controller.
 *
 * Endpoints:
 * - GET /api/projects/{id}/impact/{nodeId}
 *
 * TODO: Implement endpoints
 */
@RestController
@RequestMapping("/api/projects/{projectId}/impact")
@RequiredArgsConstructor
public class ImpactController {

    private final ImpactService impactService;

    // TODO: Add endpoint methods
}
