package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the {@code trace_endpoint} MCP tool: HTTP route -> handler -> downstream flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointTraceResponse {
    private String projectId;
    private String httpMethod;
    private String routePath;
    private EndpointInfo endpoint;
    private HandlerInfo handlerMethod;
    private String traceStrategy;
    private List<FlowStep> flowSteps;
    private List<NodeRef> relatedTypes;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointInfo {
        private String id;
        private String type;
        private String name;
        private String httpMethod;
        private String routePath;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HandlerInfo {
        private String id;
        private String name;
        private String fullName;
        private Integer lineNumber;
        private Integer endLine;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowStep {
        private int index;
        private String nodeId;
        private String type;
        private String name;
        private String fullName;
        private Integer lineNumber;
        private String relationshipType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeRef {
        private String id;
        private String type;
        private String name;
        private String fullName;
    }
}
