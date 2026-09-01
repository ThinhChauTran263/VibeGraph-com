package com.vibegraph.abuse;

import com.vibegraph.abuse.entity.RequestEvent;

import java.time.Instant;
import java.util.UUID;

import com.vibegraph.auth.domain.entity.User;

public record RequestEventResponse(
        UUID id,
        UUID userId,
        String userDisplayName,
        String userEmail,
        String apiKeyRef,
        String ipAddress,
        String route,
        String method,
        int status,
        String eventType,
        Instant occurredAt) {

    public static RequestEventResponse from(RequestEvent event) {
        return from(event, null);
    }

    public static RequestEventResponse from(RequestEvent event, User user) {
        return new RequestEventResponse(event.getId(), event.getUserId(),
                user != null ? user.getDisplayName() : null,
                user != null ? user.getEmail() : null,
                safeApiKeyRef(event.getApiKeyRef()), event.getIpAddress(), event.getRoute(), event.getMethod(),
                event.getStatus(), event.getEventType(), event.getOccurredAt());
    }

    static String safeApiKeyRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String prefix = value.substring(value.lastIndexOf(':') + 1);
        return prefix.substring(0, Math.min(prefix.length(), 8)) + "****";
    }
}
