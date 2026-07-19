package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditRetentionResponse(int retentionDays, UUID updatedBy, Instant updatedAt) {
}
