package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.ImpactAnalysisContextResponse;

public interface ImpactAnalysisAnalyzer {
    ImpactAnalysisContextResponse analyzeImpact(String projectId, String nodeQuery, int depth);
}
