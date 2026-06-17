package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the {@code search_source} MCP tool. Snippets are trimmed and redacted; paths
 * are always project-relative.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceSearchResponse {
    private String projectId;
    private String query;
    private String fileGlob;
    private List<Match> matches;
    private int totalMatches;
    private int returnedMatches;
    private boolean truncated;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Match {
        private String relativePath;
        private Integer lineNumber;
        private String snippet;
        private String nodeId;
        private String nodeType;
    }
}
