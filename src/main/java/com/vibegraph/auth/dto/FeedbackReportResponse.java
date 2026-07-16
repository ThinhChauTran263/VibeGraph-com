package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.vibegraph.auth.domain.FeedbackCategory;
import com.vibegraph.auth.domain.FeedbackReportStatus;

/**
 * Response for a single feedback report (without messages).
 * Used in list and create/close responses.
 */
public record FeedbackReportResponse(
        UUID id,
        FeedbackReportStatus status,
        FeedbackCategory category,
        String title,
        Instant createdAt,
        Instant closedAt,      // null when OPEN
        Instant deletesAfter   // null when OPEN
) {}
