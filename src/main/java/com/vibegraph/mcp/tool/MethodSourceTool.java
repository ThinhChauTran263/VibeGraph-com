package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.MethodSourceContextResponse;
import com.vibegraph.mcp.service.MethodSourceAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MethodSourceTool {

    private final MethodSourceAnalyzer methodSourceAnalyzer;

    @Tool(name = "get_method_source", description = "Read the exact source body of a single method or constructor, "
            + "resolved from the graph by node id, fully-qualified signature, or an unambiguous name. Returns the "
            + "method body, signature metadata (return type, parameter types, visibility), and related symbols "
            + "(calls, callers, reads, writes, return types). Ambiguous queries return candidates instead of guessing.")
    public MethodSourceContextResponse getMethodSource(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Method node id, fully-qualified signature (e.g. com.app.Foo.bar(String)), or an unambiguous method name") String methodQuery) {
        return methodSourceAnalyzer.readMethodSource(projectId, methodQuery);
    }
}
