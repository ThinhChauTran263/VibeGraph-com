package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactAnalysisContextResponse {
    private String projectId;
    private String nodeQuery;
    private int depth;
    private ImpactSummary summary;
    private List<NodeImpact> directImpact;
    private List<NodeImpact> transitiveImpact;
    private String riskLevel;
    private List<String> notes;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactSummary {
        private String targetId;
        private String targetType;
        private String targetName;
        private String targetFullName;
        private int directDependents;
        private int totalDependents;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeImpact {
        private String id;
        private String type;
        private String name;
        private String fullName;
        private String impactLevel;
        private int depth;
        private Integer lineNumber;
    }
}
