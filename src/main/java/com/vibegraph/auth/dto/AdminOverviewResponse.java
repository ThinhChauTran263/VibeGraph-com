package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.List;

public record AdminOverviewResponse(
        long totalUsers,
        long onlineUsers,
        long totalProjects,
        long totalReports,
        long openReports,
        long blockedUsers,
        Instant timestamp,
        List<AdminSeriesPoint> userGrowth,
        List<AdminSeriesPoint> creditConsumption,
        AdminStorageSummary storage,
        List<AdminDistributionPoint> planDistribution,
        List<AdminStorageSubject> topStorageUsers,
        List<AdminStorageSubject> topStorageProjects,
        List<AdminSecurityAlert> securityAlerts
) {
    public record AdminSeriesPoint(String label, long value, String period) {
    }

    public record AdminDistributionPoint(String label, long value) {
    }

    public record AdminStorageSummary(
            long usedBytes,
            long totalBytes,
            String sourceLabel,
            String mountPath
    ) {
    }

    public record AdminStorageSubject(
            String id,
            String name,
            String ownerEmail,
            long usedBytes
    ) {
    }

    public record AdminSecurityAlert(
            String id,
            String type,
            String severity,
            String summary,
            Instant createdAt
    ) {
    }
}
