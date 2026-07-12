package com.vibegraph.auth.dto;

import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String displayName,
        String role,
        boolean deactivated,
        boolean blocked,
        String blockedReason,
        String blockedReasonSafe,
        String planCode,
        Long storageQuotaOverrideBytes,
        Integer creditQuotaOverride,
        long quotaBytes,
        long usedBytes
) {}
