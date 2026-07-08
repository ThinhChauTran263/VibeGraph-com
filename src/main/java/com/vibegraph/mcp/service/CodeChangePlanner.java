package com.vibegraph.mcp.service;

import java.util.List;

import com.vibegraph.mcp.dto.response.CodeChangePlanResponse;

public interface CodeChangePlanner {

    CodeChangePlanResponse planCodeChange(
            String projectId,
            String changeRequest,
            List<String> entrypoints,
            List<String> targetNodes,
            Integer maxFiles,
            boolean includeSourceSnippets);
}
