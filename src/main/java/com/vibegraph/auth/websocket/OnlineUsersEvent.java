package com.vibegraph.auth.websocket;

import java.time.Instant;
import java.util.List;

import com.vibegraph.auth.dto.AdminOverviewResponse.AdminSeriesPoint;

/**
 * Snapshot pushed to admin clients over {@code /topic/admin/online-users} on
 * every sampler tick of {@code OnlineUserHistoryService}.
 */
public record OnlineUsersEvent(long onlineUsers, Instant capturedAt, List<AdminSeriesPoint> samples) {
}
