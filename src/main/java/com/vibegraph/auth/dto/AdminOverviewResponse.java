package com.vibegraph.auth.dto;

import java.time.Instant;

public record AdminOverviewResponse(
        long totalUsers,
        long onlineUsers,
        long totalProjects,
        long totalReports,
        long openReports,
        long blockedUsers,
        Instant timestamp
) {}
