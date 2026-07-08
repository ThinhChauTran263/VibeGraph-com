package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.SourceSearchResponse;

public interface SourceSearchAnalyzer {

    SourceSearchResponse searchSource(String projectId, String query, String fileGlob, String nodeType, Integer maxResults);
}
