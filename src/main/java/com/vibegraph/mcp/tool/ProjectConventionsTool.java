package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.ProjectConventionsResponse;
import com.vibegraph.mcp.service.ProjectConventionsService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProjectConventionsTool {

    private final ProjectConventionsService projectConventionsService;

    @Tool(name = "get_project_conventions", description = "Return durable repo conventions and known facts an agent "
            + "should know before editing: architecture decisions, coding conventions, current limitations, testing "
            + "commands, known traps, realtime status, and MCP tool limitations. Read-only, parsed from the curated AI "
            + "memory file. Returns a structured warning if the memory file is absent. Never returns secrets.")
    public ProjectConventionsResponse getProjectConventions() {
        return projectConventionsService.getConventions();
    }
}
