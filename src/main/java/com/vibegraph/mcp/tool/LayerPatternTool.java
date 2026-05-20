package com.vibegraph.mcp.tool;

import com.vibegraph.mcp.service.McpToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Tool: get_layer_pattern
 *
 * Returns: how to write code in a specific layer (Controller/Service/Repository).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LayerPatternTool {

    private final McpToolService mcpToolService;

    // TODO: Add @Tool method
}
