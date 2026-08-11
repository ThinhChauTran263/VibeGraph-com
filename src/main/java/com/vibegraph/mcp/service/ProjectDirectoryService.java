package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.ProjectListResponse;

/** Lists the analyzed projects owned by the current caller for MCP discovery. */
public interface ProjectDirectoryService {

    ProjectListResponse listProjects();
}
