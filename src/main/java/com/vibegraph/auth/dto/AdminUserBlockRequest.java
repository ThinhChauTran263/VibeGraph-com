package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserBlockRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 240) String safeReason
) {}
