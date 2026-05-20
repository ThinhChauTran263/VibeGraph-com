package com.vibegraph.mcp.tool;

import com.vibegraph.mcp.service.ArchitectureAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Tool: get_coding_rules
 *
 * Returns: DO/DON'T rules derived from current architecture.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CodingRulesTool {

    private final ArchitectureAnalyzer architectureAnalyzer;

    // TODO: Add @Tool method
}
