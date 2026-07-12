package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUserBlockRequest(
        @NotBlank String reason,
        @NotBlank String safeReason
) {}
