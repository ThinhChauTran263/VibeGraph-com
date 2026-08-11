package com.vibegraph.mcp.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.VerifyChangeResponse;
import com.vibegraph.mcp.service.ChangeVerifier;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VerifyChangeTool {

    private final ChangeVerifier changeVerifier;

    @Tool(name = "verify_change", description = "Pre-completion check for a code change: given the project-relative "
            + "paths of edited files, return the graph symbols in them, every API route that can reach those symbols, "
            + "related tests, and suggested test commands. Call before declaring a change done.")
    public VerifyChangeResponse verifyChange(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Project-relative paths of the changed files (e.g. from git status)") List<String> changedFiles) {
        return changeVerifier.verifyChange(projectId, changedFiles);
    }
}
