package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code suggest_test_plan}: a layered, command-backed test plan for a described
 * change. Advisory only — grounded in detected target types/layers, never invented coverage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestPlanResponse {
    private String projectId;
    private String changeDescription;
    private String riskTolerance;
    private List<TestLevel> recommendedLevels;
    private List<String> notCovered;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestLevel {
        private String level;
        private String command;
        private String rationale;
        private String failureImplication;
    }
}
