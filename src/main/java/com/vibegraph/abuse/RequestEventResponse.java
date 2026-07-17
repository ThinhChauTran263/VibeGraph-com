package com.vibegraph.abuse;

import java.time.Instant;
import java.util.UUID;

public record RequestEventResponse(
        UUID id,
        UUID userId,
        String apiKeyRef,
        String ipAddress,
        String route,
        String method,
        int status,
        String eventType,
        Instant occurredAt) {

    public static RequestEventResponse from(RequestEvent event) {
        return new RequestEventResponse(event.getId(), event.getUserId(), event.getApiKeyRef(),
                event.getIpAddress(), event.getRoute(), event.getMethod(), event.getStatus(),
                event.getEventType(), event.getOccurredAt());
    }
}
