package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the {@code get_method_source} MCP tool. Paths are always project-relative.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodSourceContextResponse {
    private String projectId;
    private String methodQuery;
    private ResolvedMethod resolvedMethod;
    private String relativePath;
    private Integer startLine;
    private Integer endLine;
    private String content;
    private boolean truncated;
    private String truncationReason;
    private RelatedSymbols related;
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
        private String fullName;
        private String ownerClass;
        private Integer lineNumber;
        private Integer endLine;
        private String returnType;
        private String paramTypes;
        private String signature;
        private String visibility;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedSymbols {
        private List<NodeRef> calls;
        private List<NodeRef> calledBy;
        private List<NodeRef> reads;
        private List<NodeRef> writes;
        private List<NodeRef> returnsTypes;
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
