package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the {@code find_references} MCP tool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceSearchResponse {
    private String projectId;
    private String symbolQuery;
    private ResolvedSymbol resolvedSymbol;
    private List<Reference> references;
    private int totalReferences;
    private int returnedReferences;
    private boolean truncated;
    private List<Candidate> candidates;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolvedSymbol {
        private String id;
        private String type;
        private String name;
        private String fullName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reference {
        private String relationshipType;
        private String direction;
        private NodeRef source;
        private NodeRef target;
        private Integer lineNumber;
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
