package com.vibegraph.auth.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminApiKeyCreateRequest(
        @NotNull(message = "userId is required")
        UUID userId,
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name) {

    public AdminApiKeyCreateRequest {
        name = name == null ? null : name.trim();
    }
}
