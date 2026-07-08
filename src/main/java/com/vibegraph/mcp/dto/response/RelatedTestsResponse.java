package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code find_related_tests}: direct and likely-related tests for a target symbol
 * or file, with per-match confidence and ready-to-run commands. Paths are relative.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatedTestsResponse {
    private String projectId;
    private String query;
    private ResolvedTarget resolvedTarget;
    private List<TestMatch> matches;
    private int totalMatches;
    private int returnedMatches;
    private boolean truncated;
    private SuggestedCommands suggestedCommands;
    private List<Candidate> candidates;
    private List<String> gaps;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolvedTarget {
        private String id;
        private String type;
        private String name;
        private String fullName;
        private String relativePath;
        private boolean frontend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestMatch {
        private String id;
        private String type;
        private String name;
        private String fullName;
        private String relativePath;
        private String matchType;
        private String confidence;
        private String evidence;
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
