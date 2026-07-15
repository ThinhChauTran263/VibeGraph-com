package com.vibegraph.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminUserUpdateQuotaRequest(
        @Min(0) @Max(8_796_093_022_207L) Long storageQuotaOverrideMb,
        @Min(0) @Max(10_000_000) Integer creditQuotaOverride
) {}
