package com.vibegraph.auth.dto;

import java.util.List;

/**
 * Detailed view of a report: the report metadata + all messages in order.
 * Used by GET /api/account/reports/{reportId}.
 */
public record FeedbackReportDetailResponse(
        FeedbackReportResponse report,
        List<FeedbackMessageResponse> messages
) {}
