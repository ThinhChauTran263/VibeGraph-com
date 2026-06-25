package com.vibegraph.diagram.service.impl;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse;
import com.vibegraph.diagram.service.UseCaseDiagramService;
import com.vibegraph.diagram.service.impl.UseCaseInferenceEngine.InferenceResult;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.ProjectService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@inheritDoc}
 *
 * <p>Reads the project graph through {@link GraphService#getFullGraph(String)} (no direct Neo4j
 * access). The business UML Use Case diagram is produced by {@link UseCaseInferenceEngine},
 * beautified by {@link BaLabelBeautifier}, enriched with generic shared-service includes by
 * {@link GenericRelationInferer}, and rendered by {@link UmlUseCaseRenderer}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UseCaseDiagramServiceImpl implements UseCaseDiagramService {

    private static final String MODE_DETAILED = "detailed";
    private static final String MODE_GROUPED = "grouped";

    private final GraphService graphService;
    private final ProjectService projectService;
    private final UseCaseInferenceEngine inferenceEngine;
    private final UmlUseCaseRenderer renderer;
    private final BaLabelBeautifier beautifier;
    private final GenericRelationInferer relationInferer;
    private final UseCaseViewProjector viewProjector;
    private final UseCaseSemanticRefiner semanticRefiner;

    @Override
    public UmlUseCaseResponse generateUmlUseCase(String projectId, String mode) {
        String normalizedMode = normalizeMode(mode);
        GraphDataResponse graph = graphService.getFullGraph(projectId);
        String systemName = beautifier.formatSystemName(resolveSystemName(projectId));

        InferenceResult result = inferenceEngine.infer(graph, normalizedMode);

        // Display-layer beautifier: rebuild human-readable labels (actors, use cases) into BA wording
        // without touching stable ids or relations, so association/generalization/dedup are preserved.
        List<UmlUseCaseResponse.Actor> actors = beautifier.beautifyActors(result.actors());
        List<UmlUseCaseResponse.UseCaseElement> useCases = beautifier.beautifyUseCases(result.useCases());
        List<UmlUseCaseResponse.Relation> relations = result.relations();

        // Display-layer relation inference (generic, no per-domain hardcoding): promote a service
        // shared by >= 2 business use cases into an included use case with <<include>> edges. Emits
        // nothing for projects without shared business services, so it is safe for any import.
        GenericRelationInferer.EnrichedModel enriched =
                relationInferer.enrich(actors, useCases, relations, result.useCaseServices());
        actors = enriched.actors();
        useCases = enriched.useCases();
        relations = enriched.relations();

        // Tier 2 (R5): optional LLM relabelling of awkward goal names, grounded to existing ids. No-op
        // (deterministic) by default; never adds/removes elements, so relations/views stay valid.
        useCases = semanticRefiner.refineLabels(systemName, useCases);

        // Honest limitation surfaced to the reader: <<extend>> models a conditional optional flow that
        // static analysis cannot recover without guessing, so it is never auto-generated.
        List<String> warnings = new java.util.ArrayList<>(result.warnings());
        warnings.add("<<extend>> relationships are not inferred: an extend models an optional, "
                + "condition-guarded flow at an extension point, which cannot be recovered reliably "
                + "from static call/inject analysis. Add them manually where the business requires.");

        String plantUml = renderer.toPlantUml(systemName, actors, useCases, relations);
        String mermaidSyntax = renderer.toMermaid(systemName, actors, useCases, relations);

        // Per-actor and per-domain projections of the same model (R4). Pure projection, so views can
        // never disagree with the full diagram.
        List<UmlUseCaseResponse.UseCaseView> views =
                viewProjector.project(systemName, actors, useCases, relations);

        return UmlUseCaseResponse.builder()
                .diagramType("usecase")
                .style("uml")
                .mode(normalizedMode)
                .systemName(systemName)
                .actors(actors)
                .useCases(useCases)
                .relations(relations)
                .warnings(warnings)
                .mermaidSyntax(mermaidSyntax)
                .plantUmlSyntax(plantUml)
                .views(views)
                .build();
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_DETAILED;
        }
        String m = mode.trim().toLowerCase(Locale.ROOT);
        if ("flat".equals(m)) {
            return MODE_DETAILED;
        }
        if ("summary".equals(m)) {
            return MODE_GROUPED;
        }
        if (!MODE_DETAILED.equals(m) && !MODE_GROUPED.equals(m)) {
            throw new IllegalArgumentException(
                    "Invalid mode '" + mode + "'. Supported modes: detailed, grouped.");
        }
        return m;
    }

    private String resolveSystemName(String projectId) {
        try {
            ProjectResponse project = projectService.getProject(projectId);
            if (project != null && project.getName() != null && !project.getName().isBlank()) {
                return project.getName();
            }
        } catch (RuntimeException ex) {
            log.debug("Could not resolve project name for system boundary: {}", ex.getMessage());
        }
        return "System";
    }
}
