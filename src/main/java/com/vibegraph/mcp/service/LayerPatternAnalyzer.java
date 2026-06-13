package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.LayerPatternResponse;

public interface LayerPatternAnalyzer {
    LayerPatternResponse analyzeLayer(String projectId, String layer);
}
