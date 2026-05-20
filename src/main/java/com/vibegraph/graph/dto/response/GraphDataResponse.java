package com.vibegraph.graph.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Full graph data response.
 * Format: { nodes: [...], edges: [...], stats: { byType: {...} } }
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
}
