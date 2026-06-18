package com.vibegraph.mcp.service;

import com.vibegraph.mcp.dto.response.FailureExplanationResponse;

public interface FailureExplainer {

    FailureExplanationResponse explainFailure(
            String projectId,
            String stackTrace,
            String testName,
            String errorMessage,
            String failingFile,
            boolean includeSource,
            Integer maxFrames);
}
