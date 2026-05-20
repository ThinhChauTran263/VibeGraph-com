package com.vibegraph.common.config;

import org.springframework.context.annotation.Configuration;

/**
 * MCP Server configuration.
 * Registers MCP tools (@Tool annotated methods) so AI coding tools (Cursor, Kiro, Claude Code)
 * can call them via Streamable HTTP transport at /mcp endpoint.
 *
 * TODO:
 * - Register MCP tool beans
 * - Configure server info (name, version)
 * - Set up authentication (API key) for SaaS phase
 */
@Configuration
public class McpServerConfig {
    // TODO: Implement MCP server bean
}
