package com.vibegraph.mcp.tool;

import com.vibegraph.diagram.service.UseCaseDiagramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Tool: get_usecase_context
 *
 * Returns: use case context for a feature.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UseCaseContextTool {

    private final UseCaseDiagramService useCaseDiagramService;

    // TODO: Add @Tool method
}
