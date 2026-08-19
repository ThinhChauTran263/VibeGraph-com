package com.vibegraph.auth.dto;

public record AccountUsageResponse(
        long usedMb,
        long limitMb,
        long remainingMb,
        /** Exact byte counters; the MB fields above are rounded display fallbacks. */
        long usedBytes,
        long limitBytes,
        long remainingBytes,
        String planCode,
        String planName,
        Long quotaOverrideMb,
        long creditsUsed,
        long creditsLimit,
        long creditsRemaining) {
}
