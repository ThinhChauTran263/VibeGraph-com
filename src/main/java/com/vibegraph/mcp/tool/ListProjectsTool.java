package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.ProjectListResponse;
import com.vibegraph.mcp.service.ProjectDirectoryService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ListProjectsTool {

    private final ProjectDirectoryService projectDirectoryService;

    @Tool(name = "list_projects", description = "List the analyzed projects owned by the caller (id, name, "
            + "analyzedAt, graph stats). Call this first to discover valid projectId values for the other tools.")
    public ProjectListResponse listProjects() {
        return projectDirectoryService.listProjects();
    }
}
