package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.RelatedTestsResponse;
import com.vibegraph.mcp.service.TestIntelligenceAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FindRelatedTestsTool {

    private final TestIntelligenceAnalyzer testIntelligenceAnalyzer;

    @Tool(name = "find_related_tests", description = "Find tests related to a target symbol or file: direct tests "
            + "that reference it in the graph (high confidence) and likely tests by naming convention "
            + "(FooTest/FooTests/FooIT/FooIntegrationTest, medium confidence). Returns relative paths, per-match "
            + "confidence, ready-to-run Maven commands (Windows + unix), and gaps when no direct test exists.")
    public RelatedTestsResponse findRelatedTests(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(required = false, description = "Target node id") String nodeId,
            @ToolParam(required = false, description = "Target class name or full name") String className,
            @ToolParam(required = false, description = "Target method id") String methodId,
            @ToolParam(required = false, description = "Target project-relative file path") String relativePath,
            @ToolParam(required = false, description = "Free-form target query") String query,
            @ToolParam(required = false, description = "Max matches to return (default 50, hard cap 100)") Integer maxResults) {
        return testIntelligenceAnalyzer.findRelatedTests(projectId, nodeId, className, methodId, relativePath, query, maxResults);
    }
}
