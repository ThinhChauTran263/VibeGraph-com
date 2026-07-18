package com.vibegraph.abuse;

import java.time.Instant;
import java.util.List;

public record SuspiciousNetworkResponse(
        String ipAddress,
        Instant minuteBucket,
        long totalRequests,
        long uniqueUsers,
        long uniqueApiKeys,
        List<SuspiciousNetworkBreakdownResponse> breakdown) {

    public static SuspiciousNetworkResponse from(
            NetworkAggregateProjection network,
            List<SuspiciousNetworkBreakdownResponse> breakdown) {
        return new SuspiciousNetworkResponse(network.getIpAddress(), network.getMinuteBucket(),
                network.getTotalRequests(), network.getUniqueUsers(), network.getUniqueApiKeys(), breakdown);
    }
}
