package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.CompileErrorExplanationResponse;
import com.vibegraph.mcp.service.CompileErrorExplainer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExplainCompileErrorTool {

    private final CompileErrorExplainer compileErrorExplainer;

    @Tool(name = "explain_compile_error", description = "Map javac/Maven compiler output back to project symbols: "
            + "which method/class each error is in, how many callers are affected, and concrete fix hints. "
            + "Paste the raw build output.")
    public CompileErrorExplanationResponse explainCompileError(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Raw javac or Maven build output containing error lines") String compilerOutput,
            @ToolParam(required = false, description = "Maximum errors to explain (default 20, cap 50)") Integer maxErrors) {
        return compileErrorExplainer.explainCompileError(projectId, compilerOutput, maxErrors);
    }
}
