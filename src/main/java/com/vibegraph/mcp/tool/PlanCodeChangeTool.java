package com.vibegraph.mcp.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.CodeChangePlanResponse;
import com.vibegraph.mcp.service.CodeChangePlanner;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlanCodeChangeTool {

    private final CodeChangePlanner codeChangePlanner;

    @Tool(name = "plan_code_change", description = "Senior-style reconnaissance before editing: from a change request, "
            + "identify candidate files and symbols (via source search + graph), impacted blast radius, a proposed edit "
            + "sequence, a test plan, risks, open questions, and a confidence level. Conservative and evidence-backed - "
            + "returns candidates/questions when ambiguous and never claims an exact patch without source evidence. "
            + "Does not modify code.")
    public CodeChangePlanResponse planCodeChange(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "The change request in plain language") String changeRequest,
            @ToolParam(required = false, description = "Optional known entrypoints (class names, routes)") List<String> entrypoints,
            @ToolParam(required = false, description = "Optional known target node ids/names") List<String> targetNodes,
            @ToolParam(required = false, description = "Max candidate files to return (default 20, hard cap 50)") Integer maxFiles,
            @ToolParam(required = false, description = "Include bounded, redacted source snippets for top candidates (default false)") Boolean includeSourceSnippets) {
        return codeChangePlanner.planCodeChange(projectId, changeRequest, entrypoints, targetNodes, maxFiles,
                Boolean.TRUE.equals(includeSourceSnippets));
    }
}
