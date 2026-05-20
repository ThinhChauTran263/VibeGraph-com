package com.vibegraph.mcp.tool;

import com.vibegraph.mcp.service.ArchitectureAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Tool: get_project_architecture
 *
 * Returns: layers, patterns, naming conventions, anti-patterns
 *
 * Used by AI tools to understand project structure before generating code.
 *
 * TODO: Implement @Tool method with @Param annotations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArchitectureTool {

    private final ArchitectureAnalyzer architectureAnalyzer;

    // TODO: Add @Tool method
}
