package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.SourceFileContextResponse;
import com.vibegraph.mcp.service.SourceFileAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SourceFileTool {

    private final SourceFileAnalyzer sourceFileAnalyzer;

    @Tool(name = "get_source_file", description = "Read a bounded, redacted slice of a project source file. "
            + "Accepts a project-relative file path or a graph node id/full name (class, method, or field) "
            + "to resolve the file. Returns relative path, language, the requested line range, declared "
            + "symbols in the file, and truncation flags. Never returns absolute paths or secret values.")
    public SourceFileContextResponse getSourceFile(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Project-relative file path, or a node id / fully-qualified name to resolve") String filePathOrNodeId,
            @ToolParam(required = false, description = "1-based first line to return (optional, defaults to 1)") Integer startLine,
            @ToolParam(required = false, description = "1-based last line to return (optional, capped to a safe window)") Integer endLine) {
        return sourceFileAnalyzer.readSourceFile(projectId, filePathOrNodeId, startLine, endLine);
    }
}
