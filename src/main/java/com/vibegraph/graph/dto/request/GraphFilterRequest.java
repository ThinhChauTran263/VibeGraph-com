package com.vibegraph.graph.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphFilterRequest {
    private List<String> nodeTypes;
    private List<String> edgeTypes;
    private String packageFilter;
    private String layerFilter;
    private String searchQuery;
}
