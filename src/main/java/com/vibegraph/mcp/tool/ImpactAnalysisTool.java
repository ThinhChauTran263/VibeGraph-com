package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.ImpactAnalysisContextResponse;
import com.vibegraph.mcp.service.ImpactAnalysisAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ImpactAnalysisTool {

    private final ImpactAnalysisAnalyzer impactAnalysisAnalyzer;

    @Tool(name = "get_impact_analysis", description = "Return blast radius, direct impact, transitive impact, risk level, notes, and warnings for a graph node. Supports impact profiles: dependency (default), structural, type-data-flow.")
    public ImpactAnalysisContextResponse getImpactAnalysis(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Target node full name or identifier") String nodeQuery,
            @ToolParam(description = "Impact traversal depth. Allowed values: 1, 2, 3, 5") int depth,
            @ToolParam(required = false, description = "Impact profile. Allowed values: dependency (default), structural, type-data-flow") String profile) {
        return impactAnalysisAnalyzer.analyzeImpact(projectId, nodeQuery, depth, profile);
    }

    /**
     * Backward-compatible overload (not exposed as an MCP tool). Defaults to the dependency profile.
     */
    public ImpactAnalysisContextResponse getImpactAnalysis(String projectId, String nodeQuery, int depth) {
        return impactAnalysisAnalyzer.analyzeImpact(projectId, nodeQuery, depth, null);
    }
}
