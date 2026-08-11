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
    private String profile;
    private ImpactSummary summary;
    private List<NodeImpact> directImpact;
    private List<NodeImpact> transitiveImpact;
    /** API routes whose handlers can reach the target — the user-visible blast radius. */
    private List<RouteImpact> affectedRoutes;
    /** Interface/override counterparts also analyzed alongside the literal target. */
    private List<String> relatedRoots;
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
    public static class RouteImpact {
        private String httpMethod;
        private String routePath;
        private String handlerFullName;
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
