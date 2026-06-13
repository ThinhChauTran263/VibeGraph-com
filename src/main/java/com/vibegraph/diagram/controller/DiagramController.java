package com.vibegraph.diagram.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.exception.ProjectNotAnalyzedException;
import com.vibegraph.diagram.dto.response.DiagramResponse;
import com.vibegraph.diagram.dto.response.UseCaseResponse;
import com.vibegraph.diagram.service.ClassDiagramService;
import com.vibegraph.diagram.service.UseCaseDiagramService;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.service.ProjectService;

import lombok.RequiredArgsConstructor;

/**
 * Diagram REST controller.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/projects/{projectId}/diagrams/usecase}</li>
 *   <li>{@code GET /api/projects/{projectId}/diagrams/class?package=...}</li>
 * </ul>
 *
 * <p>Both endpoints first validate that the project exists and is fully analyzed,
 * so callers get a clear {@code PROJECT_NOT_FOUND} (404) or
 * {@code PROJECT_NOT_ANALYZED} (409) instead of a misleading empty diagram.
 *
 * <p>Note: Sequence diagram deferred (FR-06 post-2-month scope).
 */
@RestController
@RequestMapping("/api/projects/{projectId}/diagrams")
@RequiredArgsConstructor
public class DiagramController {

    private final UseCaseDiagramService useCaseDiagramService;
    private final ClassDiagramService classDiagramService;
    private final ProjectService projectService;

    @GetMapping("/usecase")
    public ResponseEntity<ApiResponse<UseCaseResponse>> getUseCaseDiagram(@PathVariable String projectId) {
        requireAnalyzed(projectId);
        return ResponseEntity.ok(ApiResponse.success(useCaseDiagramService.generateUseCaseDiagram(projectId)));
    }

    @GetMapping("/class")
    public ResponseEntity<ApiResponse<DiagramResponse>> getClassDiagram(
            @PathVariable String projectId,
            @RequestParam(name = "package", required = false) String packageFilter) {
        requireAnalyzed(projectId);
        return ResponseEntity.ok(
                ApiResponse.success(classDiagramService.generateClassDiagram(projectId, packageFilter)));
    }

    /**
     * Validate that the project exists and has been analyzed.
     *
     * @throws com.vibegraph.common.exception.ProjectNotFoundException if unknown (→ 404)
     * @throws ProjectNotAnalyzedException                             if not yet ANALYZED (→ 409)
     */
    private void requireAnalyzed(String projectId) {
        ProjectResponse project = projectService.getProject(projectId);
        if (!ProjectStatus.ANALYZED.name().equals(project.getStatus())) {
            throw new ProjectNotAnalyzedException(
                    "Project '" + projectId + "' is not analyzed yet (status: " + project.getStatus()
                            + "). Run analysis before requesting diagrams.");
        }
    }
}
