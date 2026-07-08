package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.ArchitectureContextResponse;
import com.vibegraph.mcp.service.ArchitectureAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ArchitectureTool {

    private final ArchitectureAnalyzer architectureAnalyzer;

    @Tool(name = "get_project_architecture", description = "Return project architecture layers, counts, detected patterns, naming conventions, and warnings.")
    public ArchitectureContextResponse getProjectArchitecture(
            @ToolParam(description = "Project identifier to inspect") String projectId) {
        return architectureAnalyzer.analyzeProject(projectId);
    }
}
