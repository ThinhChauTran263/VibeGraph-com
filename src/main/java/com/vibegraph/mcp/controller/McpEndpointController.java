package com.vibegraph.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP HTTP endpoint controller.
 *
 * Exposes Streamable HTTP transport at /mcp for AI tools.
 *
 * TODO:
 * - Spring AI MCP Boot Starter usually auto-registers this
 * - This class may not be needed depending on starter version
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpEndpointController {
    // TODO: Implement if needed (Spring AI starter may auto-handle)
}
