package com.vibegraph.mcp.service;

import java.util.List;

import com.vibegraph.mcp.dto.response.VerifyChangeResponse;

/** Maps an agent's changed files to graph symbols, reachable routes, and the tests to run. */
public interface ChangeVerifier {

    VerifyChangeResponse verifyChange(String projectId, List<String> changedFiles);
}
