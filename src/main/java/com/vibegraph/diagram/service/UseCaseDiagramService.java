package com.vibegraph.diagram.service;

import com.vibegraph.diagram.dto.response.UseCaseResponse;

/**
 * Use Case diagram generator.
 *
 * <p>Derives a Mermaid {@code flowchart LR} use case diagram from the project's
 * knowledge graph:
 * <ul>
 *   <li><b>Actors</b> — {@code HTTP Client} for every detected HTTP route
 *       (Route nodes / {@code HANDLES_ROUTE} edges).</li>
 *   <li><b>Use cases</b> — the controller handler method behind each route.</li>
 * </ul>
 *
 * <p>Job/listener actors (@Scheduled, @KafkaListener, @EventListener) are part
 * of the FR-04 vision but are <em>not</em> represented in the current graph
 * data model (the parser only emits Route nodes + HANDLES_ROUTE edges; method
 * annotations beyond request mappings are not captured). They are therefore
 * skipped gracefully until the parser exposes that data.
 */
public interface UseCaseDiagramService {

    /**
     * Build the use case diagram for a project.
     *
     * @param projectId the project identifier
     * @return actors, use cases, and a valid Mermaid {@code flowchart LR} string;
     *         an empty-but-valid diagram when the project has no detected routes.
     */
    UseCaseResponse generateUseCaseDiagram(String projectId);
}
