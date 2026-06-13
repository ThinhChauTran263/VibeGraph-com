package com.vibegraph.mcp.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchitectureContextResponse {
    private String projectId;
    private Map<String, Integer> summaryCounts;
    private List<LayerSummary> layers;
    private Map<String, String> patterns;
    private Map<String, String> namingConventions;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayerSummary {
        private String name;
        private int count;
    }
}
