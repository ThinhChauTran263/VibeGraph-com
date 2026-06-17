package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.EndpointTraceResponse;
import com.vibegraph.mcp.service.EndpointTraceAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TraceEndpointTool {

    private final EndpointTraceAnalyzer endpointTraceAnalyzer;

    @Tool(name = "trace_endpoint", description = "Trace an HTTP endpoint from its route to the handler method and "
            + "downstream execution flow. Prefers inferred STEP_IN_FLOW edges and falls back to CALLS (flagged as "
            + "lower confidence) when no flow exists. Returns the endpoint, handler, ordered flow steps, and any "
            + "related data models reached along the way.")
    public EndpointTraceResponse traceEndpoint(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "HTTP method, e.g. GET, POST, PUT, DELETE") String httpMethod,
            @ToolParam(description = "Route path, e.g. /api/categories/") String routePath,
            @ToolParam(required = false, description = "Maximum trace depth from the handler (default 5, capped at 10)") Integer maxDepth) {
        return endpointTraceAnalyzer.traceEndpoint(projectId, httpMethod, routePath, maxDepth);
    }
}
