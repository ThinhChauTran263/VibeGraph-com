package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of the {@code verify_change} MCP tool: given the files an agent just changed,
 * report the graph symbols involved, the API routes that can reach them, and the tests
 * that should run before declaring the change done.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyChangeResponse {
    private String projectId;
    private List<String> changedFiles;
    private List<ChangedSymbol> changedSymbols;
    /** Minimal reverse-reachable context around changed symbols, bounded by the verifier. */
    private List<ReachableSymbol> reachableContext;
    private List<RouteRef> affectedRoutes;
    private List<TestRef> relatedTests;
    private SuggestedCommands suggestedCommands;
    private List<String> risks;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangedSymbol {
        private String id;
        private String type;
        private String name;
        private String fullName;
        private String relativePath;
        private Integer lineNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReachableSymbol {
        private String id;
        private String type;
        private String fullName;
        private Integer depth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteRef {
        private String httpMethod;
        private String routePath;
        private String handlerFullName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestRef {
        private String name;
        private String fullName;
        private String relativePath;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedCommands {
        private String windows;
        private String unix;
        private String frontend;
    }
}
