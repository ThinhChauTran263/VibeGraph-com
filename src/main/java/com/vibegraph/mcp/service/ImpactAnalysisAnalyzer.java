package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.ImpactAnalysisContextResponse;

public interface ImpactAnalysisAnalyzer {

    default ImpactAnalysisContextResponse analyzeImpact(String projectId, String nodeQuery, int depth) {
        return analyzeImpact(projectId, nodeQuery, depth, null);
    }

    ImpactAnalysisContextResponse analyzeImpact(String projectId, String nodeQuery, int depth, String profile);
}
