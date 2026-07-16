package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountProfileUpdateRequest(

        @NotBlank(message = "displayName is required")
        @Size(max = 120, message = "displayName must be at most 120 characters")
        String displayName) {

    public AccountProfileUpdateRequest {
        displayName = displayName == null ? null : displayName.trim();
    }
}
