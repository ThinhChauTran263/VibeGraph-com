package com.vibegraph.abuse;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IpBlockRequest(
        @NotBlank @Size(max = 120) String ipAddress,
        @NotBlank @Size(max = 240) String safeReason,
        Instant expiresAt,
        boolean active) {
}
