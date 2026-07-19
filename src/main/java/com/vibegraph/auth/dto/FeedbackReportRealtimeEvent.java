package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** WebSocket payload for realtime feedback/report thread updates. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackReportRealtimeEvent(
        String type,
        UUID reportId,
        FeedbackReportResponse report,
        FeedbackMessageResponse message,
        Instant timestamp
) {
    public static final String MESSAGE_ADDED = "REPORT_MESSAGE_ADDED";
    public static final String REPORT_CLOSED = "REPORT_CLOSED";

    public static FeedbackReportRealtimeEvent messageAdded(
            UUID reportId,
            FeedbackMessageResponse message
    ) {
        return new FeedbackReportRealtimeEvent(MESSAGE_ADDED, reportId, null, message, Instant.now());
    }

    public static FeedbackReportRealtimeEvent reportClosed(FeedbackReportResponse report) {
        return new FeedbackReportRealtimeEvent(REPORT_CLOSED, report.id(), report, null, Instant.now());
    }
}
