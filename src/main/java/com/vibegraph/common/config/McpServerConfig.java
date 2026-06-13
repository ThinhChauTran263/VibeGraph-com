package com.vibegraph.common.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vibegraph.mcp.tool.ArchitectureTool;
import com.vibegraph.mcp.tool.ClassContextTool;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(ArchitectureTool architectureTool, ClassContextTool classContextTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(architectureTool, classContextTool)
                .build();
    }
}
