package com.vibegraph.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AuditRetentionUpdateRequest(@Min(1) @Max(3650) int retentionDays) {
}
