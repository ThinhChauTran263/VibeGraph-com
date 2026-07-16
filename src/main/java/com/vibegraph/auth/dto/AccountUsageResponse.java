package com.vibegraph.auth.dto;

public record AccountUsageResponse(
        long usedBytes,
        long limitBytes,
        long remainingBytes,
        String planCode,
        String planName,
        Long quotaOverrideBytes,
        int creditsUsed,
        int creditsLimit,
        int creditsRemaining) {
}
