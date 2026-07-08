package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.MethodCpgContextResponse;

public interface MethodCpgAnalyzer {

    MethodCpgContextResponse analyzeMethodCpg(
            String projectId,
            String methodId,
            String className,
            String methodName,
            String query,
            boolean includeSource,
            Integer maxRelations,
            String profile);
}
