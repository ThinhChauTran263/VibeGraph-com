package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassContextResponse {
    private String projectId;
    private String query;
    private ClassInfo classInfo;
    private List<MemberInfo> methods;
    private List<MemberInfo> fields;
    private List<RelationInfo> incomingRelations;
    private List<RelationInfo> outgoingRelations;
    /** Populated instead of classInfo when the query matches more than one class. */
    private List<Candidate> candidates;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassInfo {
        private String id;
        private String type;
        private String name;
        private String fullName;
        private String layer;
        private Integer lineNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private String id;
        private String type;
        private String name;
        private String fullName;
        private String signature;
        private String visibility;
        private Integer lineNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationInfo {
        private String id;
        private String type;
        private NodeRef source;
        private NodeRef target;
        private Double confidence;
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
