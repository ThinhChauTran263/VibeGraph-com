package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.EndpointTraceResponse;

public interface EndpointTraceAnalyzer {

    EndpointTraceResponse traceEndpoint(String projectId, String httpMethod, String routePath, Integer maxDepth);
}
