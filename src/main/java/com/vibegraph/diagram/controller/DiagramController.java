package com.vibegraph.diagram.controller;

import com.vibegraph.diagram.service.ClassDiagramService;
import com.vibegraph.diagram.service.SequenceDiagramService;
import com.vibegraph.diagram.service.UseCaseDiagramService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagram REST controller.
 *
 * Endpoints:
 * - GET /api/projects/{id}/diagrams/usecase
 * - GET /api/projects/{id}/diagrams/class?package=...
 * - GET /api/projects/{id}/diagrams/sequence?entry=...
 *
 * TODO: Implement endpoints
 */
@RestController
@RequestMapping("/api/projects/{projectId}/diagrams")
@RequiredArgsConstructor
public class DiagramController {

    private final UseCaseDiagramService useCaseDiagramService;
    private final ClassDiagramService classDiagramService;
    private final SequenceDiagramService sequenceDiagramService;

    // TODO: Add endpoint methods
}
