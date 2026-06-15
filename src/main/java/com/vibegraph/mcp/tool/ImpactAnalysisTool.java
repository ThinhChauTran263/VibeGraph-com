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

    @Tool(name = "get_impact_analysis", description = "Return blast radius, direct impact, transitive impact, risk level, notes, and warnings for a graph node.")
    public ImpactAnalysisContextResponse getImpactAnalysis(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Target node full name or identifier") String nodeQuery,
            @ToolParam(description = "Impact traversal depth. Allowed values: 1, 2, 3, 5") int depth) {
        return impactAnalysisAnalyzer.analyzeImpact(projectId, nodeQuery, depth);
    }
}
