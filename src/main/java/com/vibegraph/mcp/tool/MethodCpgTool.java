package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.MethodCpgContextResponse;
import com.vibegraph.mcp.service.MethodCpgAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MethodCpgTool {

    private final MethodCpgAnalyzer methodCpgAnalyzer;

    @Tool(name = "get_method_cpg_context", description = "Return a grouped code-property-graph view of a method: "
            + "signature (params, return, thrown types), data flow (READS/WRITES/TYPE_OF/PARAMETER_TYPE/RETURNS), "
            + "control flow (CALLS, STEP_IN_FLOW steps with confidence, CATCHES), counts, and optional bounded source. "
            + "Resolve the method via methodId, className+methodName, or query. Profiles: summary, data-flow, "
            + "control-flow, full (default full). Missing deep-CPG data is reported as a limitation, never invented.")
    public MethodCpgContextResponse getMethodCpgContext(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(required = false, description = "Exact method node id") String methodId,
            @ToolParam(required = false, description = "Owner class name or full name (use with methodName)") String className,
            @ToolParam(required = false, description = "Method name (use with className)") String methodName,
            @ToolParam(required = false, description = "Free-form method query (id, full signature, or unambiguous name)") String query,
            @ToolParam(required = false, description = "Include a bounded, redacted source snippet (default false)") Boolean includeSource,
            @ToolParam(required = false, description = "Max relations per group (default 100, hard cap 500)") Integer maxRelations,
            @ToolParam(required = false, description = "Detail profile: summary | data-flow | control-flow | full (default full)") String profile) {
        return methodCpgAnalyzer.analyzeMethodCpg(
                projectId, methodId, className, methodName, query,
                Boolean.TRUE.equals(includeSource), maxRelations, profile);
    }
}
