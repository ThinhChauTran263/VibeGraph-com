package com.vibegraph.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.ClassContextResponse;
import com.vibegraph.mcp.service.ClassContextAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClassContextTool {

    private final ClassContextAnalyzer classContextAnalyzer;

    @Tool(name = "get_class_context", description = "Return class details, methods, fields, incoming relations, outgoing relations, and warnings.")
    public ClassContextResponse getClassContext(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Class id, full name, or simple name to inspect") String classQuery) {
        return classContextAnalyzer.analyzeClass(projectId, classQuery);
    }
}
