package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.vibegraph.auth.domain.ApiKey;

public record ApiKeyResponse(
        UUID id,
        String keyPrefix,
        String name,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant disabledAt) {

    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getKeyPrefix(),
                apiKey.getName(),
                apiKey.getCreatedAt(),
                apiKey.getLastUsedAt(),
                apiKey.getExpiresAt(),
                apiKey.getDisabledAt());
    }
}
