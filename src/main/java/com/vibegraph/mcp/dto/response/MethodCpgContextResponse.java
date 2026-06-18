package com.vibegraph.mcp.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code get_method_cpg_context}: a senior-friendly, grouped view of a method's
 * code-property-graph relations (reads/writes/calls/flow/catches/types). Paths are relative.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodCpgContextResponse {
    private String projectId;
    private String methodQuery;
    private String profile;
    private ResolvedMethod resolvedMethod;
    private Signature signature;
    private DataFlow dataFlow;
    private ControlFlow controlFlow;
    private Map<String, Integer> counts;
    private SourceSnippet source;
    private boolean truncated;
    private List<Candidate> candidates;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolvedMethod {
        private String id;
        private String name;
        private String ownerClass;
        private String relativePath;
        private Integer lineNumber;
        private Integer endLine;
        private String visibility;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Signature {
        private String returnType;
        private List<Parameter> parameters;
        private List<String> thrownTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameter {
        private int position;
        private String name;
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataFlow {
        private List<NodeRef> reads;
        private List<NodeRef> writes;
        private List<NodeRef> typeLinks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ControlFlow {
        private List<NodeRef> calls;
        private List<FlowStep> flowSteps;
        private List<NodeRef> catches;
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
        private Integer lineNumber;
        private String relationshipType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowStep {
        private int index;
        private String nodeId;
        private String name;
        private String fullName;
        private Integer lineNumber;
        private Double confidence;
        private String relationshipType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceSnippet {
        private String relativePath;
        private Integer startLine;
        private Integer endLine;
        private String content;
        private boolean truncated;
        private String truncationReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private String id;
        private String type;
        private String name;
        private String fullName;
    }
}
