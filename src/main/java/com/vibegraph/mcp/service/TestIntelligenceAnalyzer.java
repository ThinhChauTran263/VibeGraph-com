package com.vibegraph.mcp.service;

import java.util.List;

import com.vibegraph.mcp.dto.response.RelatedTestsResponse;
import com.vibegraph.mcp.dto.response.TestPlanResponse;

public interface TestIntelligenceAnalyzer {

    RelatedTestsResponse findRelatedTests(
            String projectId, String nodeId, String className, String methodId,
            String relativePath, String query, Integer maxResults);

    TestPlanResponse suggestTestPlan(
            String projectId, String changeDescription, List<String> targetNodes,
            List<String> files, String riskTolerance);
}
