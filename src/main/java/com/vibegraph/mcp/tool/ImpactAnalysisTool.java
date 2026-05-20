package com.vibegraph.mcp.tool;

import com.vibegraph.graph.service.ImpactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Tool: get_impact_analysis
 *
 * Returns: blast radius when changing a target node (class/method).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImpactAnalysisTool {

    private final ImpactService impactService;

    // TODO: Add @Tool method
}
