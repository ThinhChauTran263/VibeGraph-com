package com.vibegraph.common.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vibegraph.mcp.tool.ArchitectureTool;
import com.vibegraph.mcp.tool.ClassContextTool;
import com.vibegraph.mcp.tool.FindReferencesTool;
import com.vibegraph.mcp.tool.ImpactAnalysisTool;
import com.vibegraph.mcp.tool.LayerPatternTool;
import com.vibegraph.mcp.tool.MethodSourceTool;
import com.vibegraph.mcp.tool.SearchSourceTool;
import com.vibegraph.mcp.tool.SourceFileTool;
import com.vibegraph.mcp.tool.TraceEndpointTool;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            ArchitectureTool architectureTool,
            ClassContextTool classContextTool,
            ImpactAnalysisTool impactAnalysisTool,
            LayerPatternTool layerPatternTool,
            SourceFileTool sourceFileTool,
            MethodSourceTool methodSourceTool,
            SearchSourceTool searchSourceTool,
            FindReferencesTool findReferencesTool,
            TraceEndpointTool traceEndpointTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        architectureTool,
                        classContextTool,
                        impactAnalysisTool,
                        layerPatternTool,
                        sourceFileTool,
                        methodSourceTool,
                        searchSourceTool,
                        findReferencesTool,
                        traceEndpointTool)
                .build();
    }
}
