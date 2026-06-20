package com.vibegraph.graph.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full graph data response.
 * Format: { nodes: [...], edges: [...], stats: { byType: {...} }, meta: {...} }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphDataResponse {
    private List<NodeDto> nodes;
    private List<EdgeDto> edges;
    private Map<String, Integer> nodeStats;
    private Map<String, Integer> edgeStats;

    /**
     * Payload guardrail metadata for the HTTP graph endpoint. Null for internal/full snapshots
     * (e.g. websocket FULL_UPDATE, diagram inference) where no capping is applied.
     */
    private Meta meta;

    /**
     * Truncation metadata describing how the returned payload relates to the full backend graph.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        /** True when nodes and/or edges were dropped to fit the limits. */
        private boolean truncated;
        /** Total nodes in the uncapped backend graph. */
        private int totalNodes;
        /** Total edges in the uncapped backend graph. */
        private int totalEdges;
        /** Nodes actually returned in this payload. */
        private int returnedNodes;
        /** Edges actually returned in this payload. */
        private int returnedEdges;
        /** Effective node limit applied. */
        private int nodeLimit;
        /** Effective edge limit applied. */
        private int edgeLimit;
        /** Machine-readable reason when truncated (e.g. {@code "GRAPH_TOO_LARGE"}); null otherwise. */
        private String reason;
    }
}
