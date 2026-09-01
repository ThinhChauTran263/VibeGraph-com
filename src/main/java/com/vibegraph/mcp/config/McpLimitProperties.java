package com.vibegraph.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import lombok.Getter;

/** Runtime guardrails shared by graph-reading MCP tools. */
@Getter
@Validated
@Configuration
@ConfigurationProperties(prefix = "vibegraph.mcp")
public class McpLimitProperties {

    /** Maximum graph nodes a single MCP tool may process. */
    @Min(1)
    private int maxNodes = 100_000;

    /** Maximum graph edges a single MCP tool may process. */
    @Min(1)
    private int maxEdges = 200_000;

    public void setMaxNodes(int maxNodes) {
        this.maxNodes = requirePositive("maxNodes", maxNodes);
    }

    public void setMaxEdges(int maxEdges) {
        this.maxEdges = requirePositive("maxEdges", maxEdges);
    }

    private int requirePositive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1");
        }
        return value;
    }
}
