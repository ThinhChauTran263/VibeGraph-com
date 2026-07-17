package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID announcementId,
        String title,
        String body,
        String creatorName,
        String creatorDisplayName,
        String creatorEmail,
        Instant createdAt,
        String severity,
        String type,
        boolean dismissible,
        boolean read,
        Instant readAt,
        Instant dismissedAt) {
}
