package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.MethodSourceContextResponse;

public interface MethodSourceAnalyzer {

    MethodSourceContextResponse readMethodSource(String projectId, String methodQuery);
}
