package com.vibegraph.auth.dto;

public record AdminUserUpdateQuotaRequest(
        Long storageQuotaOverrideMb,
        Integer creditQuotaOverride
) {}
