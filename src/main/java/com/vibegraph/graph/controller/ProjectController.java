package com.vibegraph.graph.controller;

import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project management REST controller.
 *
 * Endpoints:
 * - POST /api/projects                       (register)
 * - GET  /api/projects                       (list)
 * - GET  /api/projects/{id}                  (detail)
 * - POST /api/projects/{id}/analyze          (trigger analysis)
 * - GET  /api/projects/{id}/status           (analysis status)
 * - DELETE /api/projects/{id}                (delete)
 *
 * TODO: Implement endpoints
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final AnalyzeService analyzeService;

    // TODO: Add endpoint methods
}
