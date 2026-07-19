package com.vibegraph.graph.dto.request;

import jakarta.validation.constraints.Size;

/** Request payload for creating an empty CLI-synced repository workspace. */
public record CliRepositoryCreateRequest(
        @Size(max = 120, message = "name must be at most 120 characters")
        String name) {

    public CliRepositoryCreateRequest {
        name = name == null ? null : name.trim();
    }
}
