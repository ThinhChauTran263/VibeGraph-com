package com.vibegraph.abuse;

import java.time.Instant;
import java.util.UUID;

public record RequestAggregateResponse(
        UUID userId,
        String userDisplayName,
        String userEmail,
        String apiKeyRef,
        String ipAddress,
        Instant minuteBucket,
        long requestsPerMinute) {

    public static RequestAggregateResponse from(RequestAggregateProjection row) {
        return new RequestAggregateResponse(row.getUserId(), row.getUserDisplayName(), row.getUserEmail(),
                RequestEventResponse.safeApiKeyRef(row.getApiKeyRef()), row.getIpAddress(),
                row.getMinuteBucket(), row.getRequestCount());
    }
}
