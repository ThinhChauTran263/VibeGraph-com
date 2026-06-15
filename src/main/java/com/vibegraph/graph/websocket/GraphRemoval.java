package com.vibegraph.graph.websocket;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The {@code removed} portion of an incremental graph update: ids of nodes and
 * edges to drop. Null fields are omitted from the JSON payload, matching the
 * frontend's optional shape ({@code { nodeIds?, edgeIds? }}).
 *
 * @param nodeIds ids of nodes to remove; may be null
 * @param edgeIds ids of edges to remove; may be null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphRemoval(
        List<String> nodeIds,
        List<String> edgeIds
) {
}
