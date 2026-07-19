package com.vibegraph.graph.dto.response;

import java.util.List;

import com.vibegraph.auth.dto.ApiKeyCreateResponse;

/** One-time CLI repository setup response. The API key secret is never listable again. */
public record CliRepositorySetupResponse(
        ProjectResponse project,
        ApiKeyCreateResponse apiKey,
        List<String> commands) {
}
