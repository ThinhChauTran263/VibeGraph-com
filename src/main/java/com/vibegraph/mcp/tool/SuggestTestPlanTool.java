package com.vibegraph.mcp.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.TestPlanResponse;
import com.vibegraph.mcp.service.TestIntelligenceAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SuggestTestPlanTool {

    private final TestIntelligenceAnalyzer testIntelligenceAnalyzer;

    @Tool(name = "suggest_test_plan", description = "Suggest a layered test plan for a described change: recommended "
            + "test levels (unit, integration/Testcontainers, mcp-live/api, frontend-unit, browser-smoke) with exact "
            + "commands, rationale, and what a failure would imply, plus what is NOT covered. Advisory and grounded in "
            + "the described change and target types — it never claims coverage that does not exist.")
    public TestPlanResponse suggestTestPlan(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Description of the intended change") String changeDescription,
            @ToolParam(required = false, description = "Optional target node ids/names for context") List<String> targetNodes,
            @ToolParam(required = false, description = "Optional changed/affected file paths for context") List<String> files,
            @ToolParam(required = false, description = "Risk tolerance: low | medium | high (default medium)") String riskTolerance) {
        return testIntelligenceAnalyzer.suggestTestPlan(projectId, changeDescription, targetNodes, files, riskTolerance);
    }
}
