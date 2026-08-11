package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of the {@code list_projects} MCP tool: the analyzed projects OWNED by the caller,
 * so an agent can discover valid {@code projectId} values without out-of-band configuration.
 * Absolute server paths are intentionally never included.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectListResponse {
    private List<ProjectInfo> projects;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectInfo {
        private String id;
        private String name;
        /** ISO-8601 instant of the last analyze, or null when unknown. */
        private String analyzedAt;
        private int totalFiles;
        private int totalNodes;
        private int totalEdges;
    }
}
