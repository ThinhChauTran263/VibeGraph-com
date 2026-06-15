package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.LayerPatternResponse;
import com.vibegraph.mcp.service.LayerPatternAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LayerPatternTool {

    private final LayerPatternAnalyzer layerPatternAnalyzer;

    @Tool(name = "get_layer_pattern", description = "Return examples, dependency patterns, naming conventions, rules, notes, and warnings for an architecture layer.")
    public LayerPatternResponse getLayerPattern(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Layer name such as CONTROLLER, SERVICE, REPOSITORY, CONFIG, or ROUTE") String layer) {
        return layerPatternAnalyzer.analyzeLayer(projectId, layer);
    }
}
