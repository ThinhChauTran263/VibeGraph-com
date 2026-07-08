package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.SourceFileContextResponse;

public interface SourceFileAnalyzer {

    SourceFileContextResponse readSourceFile(String projectId, String filePathOrNodeId, Integer startLine, Integer endLine);
}
