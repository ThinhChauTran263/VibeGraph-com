package com.vibegraph.graph.websocket;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * A bucket of nodes/edges for an incremental graph update (the {@code added}
 * or {@code modified} portion of a {@link GraphUpdateEvent}).
 *
 * Null fields are omitted from the JSON payload, matching the optional shape
 * the frontend consumer expects ({@code { nodes?, edges? }}).
 *
 * @param nodes nodes that were added/modified; may be null
 * @param edges edges that were added/modified; may be null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphChangeSet(
        List<NodeDto> nodes,
        List<EdgeDto> edges
) {
}
