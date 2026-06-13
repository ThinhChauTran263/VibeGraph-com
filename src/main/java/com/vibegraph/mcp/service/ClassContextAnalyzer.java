package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.ClassContextResponse;

public interface ClassContextAnalyzer {

    ClassContextResponse analyzeClass(String projectId, String classQuery);
}
