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
public class LayerPatternResponse {
    private String projectId;
    private String requestedLayer;
    private String normalizedLayer;
    private String description;
    private List<LayerExample> examples;
    private List<DependencySummary> commonDependencies;
    private Map<String, String> namingConventions;
    private List<String> doRules;
    private List<String> dontRules;
    private List<String> patternNotes;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayerExample {
        private String id;
        private String type;
        private String name;
        private String fullName;
        private Integer lineNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DependencySummary {
        private String relationType;
        private String targetLayer;
        private int count;
    }
}
