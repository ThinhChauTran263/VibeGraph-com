package com.vibegraph.abuse;

import java.util.UUID;

public record SuspiciousNetworkBreakdownResponse(
        UUID userId,
        String userDisplayName,
        String userEmail,
        String apiKeyRef,
        long requests) {

    public static SuspiciousNetworkBreakdownResponse from(NetworkBreakdownProjection row) {
        return new SuspiciousNetworkBreakdownResponse(row.getUserId(), row.getUserDisplayName(),
                row.getUserEmail(), RequestEventResponse.safeApiKeyRef(row.getApiKeyRef()), row.getRequests());
    }
}
