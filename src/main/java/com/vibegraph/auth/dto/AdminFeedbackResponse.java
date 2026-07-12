package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminFeedbackResponse(
        UUID id,
        UUID userId,
        String status,
        String category,
        String title,
        Instant createdAt,
        Instant closedAt,
        Instant deleteAfter
) {}
