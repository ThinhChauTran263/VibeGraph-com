package com.vibegraph.graph.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vibegraph.graph.dto.response.GraphDataResponse;

/**
 * Payload broadcast to {@code /topic/projects/{projectId}/updates} when a
 * project's graph changes.
 *
 * Two variants, discriminated by {@link #type}:
 * <ul>
 *   <li>{@link #FULL_UPDATE} — carries the complete {@link GraphDataResponse}
 *       in {@link #graph}; the incremental fields are null.</li>
 *   <li>{@link #INCREMENTAL} — carries {@link #added}/{@link #modified}/
 *       {@link #removed} diffs; {@link #graph} is null.</li>
 * </ul>
 *
 * Null fields are omitted from the JSON so each variant serializes to exactly
 * the shape the frontend consumer (T60) validates:
 * <pre>
 * { "type": "FULL_UPDATE",  "projectId": "...", "graph": { ... } }
 * { "type": "INCREMENTAL", "projectId": "...", "added": {...}, "modified": {...}, "removed": {...} }
 * </pre>
 *
 * @param type      {@link #FULL_UPDATE} or {@link #INCREMENTAL}
 * @param projectId the project this update applies to
 * @param graph     full graph snapshot (FULL_UPDATE only); may be null
 * @param added     added nodes/edges (INCREMENTAL only); may be null
 * @param modified  modified nodes/edges (INCREMENTAL only); may be null
 * @param removed   removed node/edge ids (INCREMENTAL only); may be null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphUpdateEvent(
        String type,
        String projectId,
        GraphDataResponse graph,
        GraphChangeSet added,
        GraphChangeSet modified,
        GraphRemoval removed
) {
    /** Discriminator value for a full-graph replacement event. */
    public static final String FULL_UPDATE = "FULL_UPDATE";
    /** Discriminator value for an incremental diff event. */
    public static final String INCREMENTAL = "INCREMENTAL";

    /** Build a FULL_UPDATE event carrying the complete graph snapshot. */
    public static GraphUpdateEvent fullUpdate(String projectId, GraphDataResponse graph) {
        return new GraphUpdateEvent(FULL_UPDATE, projectId, graph, null, null, null);
    }

    /** Build an INCREMENTAL event carrying the added/modified/removed diffs. */
    public static GraphUpdateEvent incremental(
            String projectId,
            GraphChangeSet added,
            GraphChangeSet modified,
            GraphRemoval removed
    ) {
        return new GraphUpdateEvent(INCREMENTAL, projectId, null, added, modified, removed);
    }
}
