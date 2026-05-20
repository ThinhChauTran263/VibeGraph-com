package com.vibegraph.mcp.tool;

import com.vibegraph.mcp.service.McpToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Tool: get_class_context
 *
 * Returns: related classes, class diagram, methods, dependencies for a given class.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClassContextTool {

    private final McpToolService mcpToolService;

    // TODO: Add @Tool method getClassContext(@Param("className") String className)
}
