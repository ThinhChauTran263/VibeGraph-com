package com.vibegraph.diagram.controller;

import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.exception.ProjectNotAnalyzedException;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
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
 *   <li>{@code GET /api/projects/{projectId}/diagrams/usecase?style=uml&mode=flat|grouped} —
 *       inferred business UML use case diagram</li>
 * </ul>
 *
 * <p>All endpoints first validate that the project exists and is fully analyzed,
 * so callers get a clear {@code PROJECT_NOT_FOUND} (404) or
 * {@code PROJECT_NOT_ANALYZED} (409) instead of a misleading empty diagram. Invalid
 * {@code style}/{@code mode} values yield {@code BAD_REQUEST} (400).
 *
 */
@RestController
@RequestMapping("/api/projects/{projectId}/diagrams")
@RequiredArgsConstructor
public class DiagramController {

    private static final String STYLE_UML = "uml";

    private final UseCaseDiagramService useCaseDiagramService;
    private final ProjectService projectService;
    private final ProjectOwnershipGuard ownershipGuard;
    private final FeatureGateService featureGateService;

    @GetMapping("/usecase")
    public ResponseEntity<ApiResponse<Object>> getUseCaseDiagram(
            @PathVariable String projectId,
            @RequestParam(name = "style", required = false, defaultValue = STYLE_UML) String style,
            @RequestParam(name = "mode", required = false) String mode) {
        ownershipGuard.assertOwner(projectId);
        featureGateService.assertEnabled(FeatureGateService.USECASE_GENERATE);
        requireAnalyzed(projectId);
        String normalizedStyle = style == null ? STYLE_UML : style.trim().toLowerCase(Locale.ROOT);
        if (!STYLE_UML.equals(normalizedStyle)) {
            throw new IllegalArgumentException(
                    "Invalid style '" + style + "'. Supported styles: uml.");
        }
        return ResponseEntity.ok(ApiResponse.success(useCaseDiagramService.generateUmlUseCase(projectId, mode)));
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
