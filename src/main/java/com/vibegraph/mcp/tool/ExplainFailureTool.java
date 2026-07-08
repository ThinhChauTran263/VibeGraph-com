package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.FailureExplanationResponse;
import com.vibegraph.mcp.service.FailureExplainer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExplainFailureTool {

    private final FailureExplainer failureExplainer;

    @Tool(name = "explain_failure_path", description = "Map a Java stack trace or test failure to in-project code: "
            + "parses frames, resolves them to classes/methods/source, lists the method's calls and whether it handles "
            + "a route, identifies likely root-cause locations, and proposes debugging steps. For a test name it finds "
            + "the test and its production targets. External-only traces are reported as such - nothing is invented.")
    public FailureExplanationResponse explainFailurePath(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(required = false, description = "Java stack trace text") String stackTrace,
            @ToolParam(required = false, description = "Failing test name (simple, full, or Class.method)") String testName,
            @ToolParam(required = false, description = "Error message text") String errorMessage,
            @ToolParam(required = false, description = "Reported failing file (project-relative path)") String failingFile,
            @ToolParam(required = false, description = "Include bounded, redacted source around frames (default false)") Boolean includeSource,
            @ToolParam(required = false, description = "Max stack frames to parse (default 20, hard cap 100)") Integer maxFrames) {
        return failureExplainer.explainFailure(projectId, stackTrace, testName, errorMessage, failingFile,
                Boolean.TRUE.equals(includeSource), maxFrames);
    }
}
