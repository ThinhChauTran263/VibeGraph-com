package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.SourceSearchResponse;
import com.vibegraph.mcp.service.SourceSearchAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SearchSourceTool {

    private final SourceSearchAnalyzer sourceSearchAnalyzer;

    @Tool(name = "search_source", description = "Case-insensitive literal text search across allow-listed project "
            + "source files. Returns short, redacted snippets with relative path and line number, and maps a match "
            + "to its enclosing graph symbol when possible. Supports an optional file glob and node-type filter. "
            + "Build output, archives, secrets, and binaries are never searched.")
    public SourceSearchResponse searchSource(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Literal text to search for (not a regular expression)") String query,
            @ToolParam(required = false, description = "Optional glob limiting files, e.g. **/*.java") String fileGlob,
            @ToolParam(required = false, description = "Optional node type filter (e.g. Method, Class) applied to mapped symbols") String nodeType,
            @ToolParam(required = false, description = "Maximum matches to return (default 50, hard cap 100)") Integer maxResults) {
        return sourceSearchAnalyzer.searchSource(projectId, query, fileGlob, nodeType, maxResults);
    }
}
