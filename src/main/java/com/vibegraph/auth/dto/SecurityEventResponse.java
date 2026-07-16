package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.vibegraph.auth.domain.SecurityEvent;

public record SecurityEventResponse(
        UUID id,
        String eventType,
        String severity,
        UUID subjectUserId,
        String apiKeyRef,
        String source,
        String description,
        Instant createdAt) {

    public static SecurityEventResponse from(SecurityEvent event) {
        return new SecurityEventResponse(
                event.getId(),
                event.getEventType(),
                event.getSeverity(),
                event.getSubjectUserId(),
                event.getApiKeyRef(),
                event.getSource(),
                event.getDescription(),
                event.getCreatedAt());
    }
}
