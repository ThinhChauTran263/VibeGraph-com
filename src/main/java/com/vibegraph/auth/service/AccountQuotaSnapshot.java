package com.vibegraph.auth.service;

public record AccountQuotaSnapshot(
        long usedBytes,
        long limitBytes,
        long remainingBytes,
        String planCode,
        String planName,
        Long quotaOverrideBytes) {
}
