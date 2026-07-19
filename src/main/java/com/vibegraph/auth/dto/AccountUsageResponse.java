package com.vibegraph.auth.dto;

public record AccountUsageResponse(
        long usedMb,
        long limitMb,
        long remainingMb,
        String planCode,
        String planName,
        Long quotaOverrideMb,
        long creditsUsed,
        long creditsLimit,
        long creditsRemaining) {
}
