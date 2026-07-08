package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the {@code get_source_file} MCP tool. Paths are always project-relative —
 * never an absolute host path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceFileContextResponse {
    private String projectId;
    private String query;
    private String nodeId;
    private String relativePath;
    private String language;
    private Integer startLine;
    private Integer endLine;
    private Integer totalLines;
    private String content;
    private boolean truncated;
    private String truncationReason;
    private List<SymbolInfo> symbols;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SymbolInfo {
        private String id;
        private String type;
        private String name;
        private String fullName;
        private Integer lineNumber;
        private Integer endLine;
    }
}
