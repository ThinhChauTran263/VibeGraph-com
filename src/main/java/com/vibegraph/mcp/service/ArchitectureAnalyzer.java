package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.ArchitectureContextResponse;

public interface ArchitectureAnalyzer {

    ArchitectureContextResponse analyzeProject(String projectId);
}
