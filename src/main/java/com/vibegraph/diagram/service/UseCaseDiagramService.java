package com.vibegraph.diagram.service;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse;

/**
 * Use Case diagram generator.
 *
 * <p>Produces the inferred business-level <b>UML Use Case</b> diagram from the project's knowledge
 * graph (read via {@code GraphService#getFullGraph}, no direct Neo4j access): inferred business
 * actors (Guest/User/Admin) and goal-level use cases, a system boundary, plus a PlantUML source
 * alongside a Mermaid fallback.
 *
 * <p>Job/listener actors (@Scheduled, @KafkaListener, @EventListener) are not yet represented in
 * the graph data model (the parser emits only Route/APIEndpoint nodes + HANDLES_ROUTE edges) and
 * are skipped gracefully.
 */
public interface UseCaseDiagramService {

    /**
     * Build the inferred business-level UML Use Case diagram for a project.
     *
     * @param projectId the project identifier
     * @param mode      {@code "detailed"}/{@code "flat"} or {@code "summary"}/{@code "grouped"}
     *                  (the canonical model is mode-independent; retained for API compatibility)
     * @return inferred actors, business use cases, relations, warnings, and both Mermaid and
     *         PlantUML renderings.
     */
    UmlUseCaseResponse generateUmlUseCase(String projectId, String mode);
}
