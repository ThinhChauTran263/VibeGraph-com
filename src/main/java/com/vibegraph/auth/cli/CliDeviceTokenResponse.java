package com.vibegraph.auth.cli;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vibegraph.auth.dto.ApiKeyResponse;

/** Poll result; the API-key secret is returned only on the single approved exchange. */
public record CliDeviceTokenResponse(
        String status,
        String apiKey,
        UUID apiKeyId,
        String projectId,
        String projectName,
        Instant expiresAt,
        List<ApiKeyResponse> availableKeys) {

    public CliDeviceTokenResponse(
            String status, String apiKey, String projectId, String projectName, Instant expiresAt) {
        this(status, apiKey, null, projectId, projectName, expiresAt, List.of());
    }

    public static CliDeviceTokenResponse pending(Instant expiresAt) {
        return new CliDeviceTokenResponse("PENDING", null, null, null, null, expiresAt, List.of());
    }
}
