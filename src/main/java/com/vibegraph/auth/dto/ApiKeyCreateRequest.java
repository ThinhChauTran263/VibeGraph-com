package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyCreateRequest(
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,
        @NotBlank(message = "projectId is required")
        @Size(max = 64, message = "projectId must be at most 64 characters")
        String projectId) {

    public ApiKeyCreateRequest {
        name = name == null ? null : name.trim();
        projectId = projectId == null ? null : projectId.trim();
    }

    public ApiKeyCreateRequest(String name) {
        this(name, null);
    }
}
