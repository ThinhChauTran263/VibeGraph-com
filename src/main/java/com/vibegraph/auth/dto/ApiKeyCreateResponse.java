package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyCreateResponse(
        UUID id,
        String keyPrefix,
        String name,
        String secretKey,
        Instant createdAt,
        Instant expiresAt) {
}
