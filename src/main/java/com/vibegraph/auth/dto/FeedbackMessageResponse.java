package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.vibegraph.auth.domain.FeedbackSenderRole;

/** Response for a single message in a report thread. */
public record FeedbackMessageResponse(
        UUID id,
        FeedbackSenderRole senderRole,
        String body,
        Instant createdAt
) {}
