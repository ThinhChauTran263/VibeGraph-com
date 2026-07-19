package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.vibegraph.auth.domain.ApiKey;
import com.vibegraph.auth.domain.ProjectOwnership;

public record ApiKeyResponse(
        UUID id,
        String keyPrefix,
        String name,
        ProjectBindingResponse project,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant disabledAt,
        com.vibegraph.auth.domain.ApiKeyDisabledBy disabledBy,
        String disabledReason,
        Instant lockedAt,
        String lockedBy,
        Instant deletedAt,
        boolean locked,
        boolean canDelete) {

    public ApiKeyResponse(
            UUID id,
            String keyPrefix,
            String name,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            Instant disabledAt) {
        this(id, keyPrefix, name, null, createdAt, lastUsedAt, expiresAt, disabledAt,
                null, null, null, null, null, false, true);
    }

    public static ApiKeyResponse from(ApiKey apiKey, ProjectOwnership project) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getKeyPrefix(),
                apiKey.getName(),
                project == null ? null : ProjectBindingResponse.from(project),
                apiKey.getCreatedAt(),
                apiKey.getLastUsedAt(),
                apiKey.getExpiresAt(),
                apiKey.getDisabledAt(),
                apiKey.getDisabledBy(),
                apiKey.getDisabledReason(),
                apiKey.getDisabledBy() == com.vibegraph.auth.domain.ApiKeyDisabledBy.ADMIN
                        ? apiKey.getDisabledAt() : null,
                apiKey.getLockedBy(),
                apiKey.getDeletedAt(),
                apiKey.getDisabledBy() == com.vibegraph.auth.domain.ApiKeyDisabledBy.ADMIN,
                apiKey.getDisabledBy() != com.vibegraph.auth.domain.ApiKeyDisabledBy.ADMIN);
    }

    public static ApiKeyResponse from(ApiKey apiKey) {
        return from(apiKey, null);
    }
}
