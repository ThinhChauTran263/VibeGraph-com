package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code plan_code_change}: a conservative, evidence-backed reconnaissance plan
 * (candidate files/symbols, impact, steps, tests, risks, open questions). Paths are relative.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeChangePlanResponse {
    private String projectId;
    private String changeRequest;
    private String summary;
    private String confidence;
    private List<CandidateFile> candidateFiles;
    private List<CandidateSymbol> candidateSymbols;
    private ImpactSummary impact;
    private List<String> proposedSteps;
    private List<String> testPlan;
    private List<String> risks;
    private List<String> openQuestions;
    private List<String> doNotTouch;
    private List<String> evidence;
    private List<SourceSnippet> sourceSnippets;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateFile {
        private String relativePath;
        private int score;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateSymbol {
        private String id;
        private String type;
        private String name;
        private String fullName;
        private String layer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactSummary {
        private String targetFullName;
        private Integer directDependents;
        private Integer totalDependents;
        private String riskLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceSnippet {
        private String relativePath;
        private Integer startLine;
        private Integer endLine;
        private String content;
        private boolean truncated;
    }
}
