package com.vibegraph.common.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vibegraph.mcp.tool.ArchitectureTool;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(ArchitectureTool architectureTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(architectureTool)
                .build();
    }
}
