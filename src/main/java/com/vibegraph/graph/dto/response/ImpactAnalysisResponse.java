package com.vibegraph.graph.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Blast radius analysis result.
 *
 * Risk levels:
 * - LOW: < 5 direct dependents
 * - MEDIUM: 5-15 direct dependents
 * - HIGH: 15+ direct dependents OR critical path
 * - CRITICAL: hub node (50+ dependents)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ImpactAnalysisResponse {
    private NodeDto target;
    private String riskLevel;
    private int directDependents;
    private int totalDependents;
    private List<NodeDto> willBreak;
    private List<NodeDto> likelyAffected;
    private List<NodeDto> mayNeedTesting;
}
