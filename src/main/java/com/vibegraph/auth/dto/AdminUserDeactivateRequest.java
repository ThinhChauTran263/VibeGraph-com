package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserDeactivateRequest(
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason,
        @NotBlank(message = "safeReason is required")
        @Size(max = 240, message = "safeReason must be at most 240 characters")
        String safeReason) {
}
