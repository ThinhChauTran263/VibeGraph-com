package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.CompileErrorExplanationResponse;

/** Maps javac/Maven compiler output back to graph symbols with actionable fix hints. */
public interface CompileErrorExplainer {

    CompileErrorExplanationResponse explainCompileError(String projectId, String compilerOutput, Integer maxErrors);
}
