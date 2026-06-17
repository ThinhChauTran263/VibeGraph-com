package com.vibegraph.mcp.service;

import java.util.List;

import com.vibegraph.mcp.dto.response.ReferenceSearchResponse;

public interface ReferenceAnalyzer {

    ReferenceSearchResponse findReferences(
            String projectId, String symbolQuery, List<String> relationshipTypes, String direction, Integer maxResults);
}
