package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyCreateResponse(
        UUID id,
        String keyPrefix,
        String name,
        String secretKey,
        ProjectBindingResponse project,
        Instant createdAt,
        Instant expiresAt) {

    public ApiKeyCreateResponse(
            UUID id,
            String keyPrefix,
            String name,
            String secretKey,
            Instant createdAt,
            Instant expiresAt) {
        this(id, keyPrefix, name, secretKey, null, createdAt, expiresAt);
    }
}
