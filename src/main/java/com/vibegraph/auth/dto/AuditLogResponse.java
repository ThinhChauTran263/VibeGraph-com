package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.vibegraph.auth.domain.AuditLog;

public record AuditLogResponse(
        UUID id,
        String action,
        UUID actorUserId,
        UUID targetUserId,
        String targetType,
        String targetId,
        String outcome,
        String ipAddress,
        String details,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getActorUserId(),
                log.getTargetUserId(),
                log.getTargetType(),
                log.getTargetId(),
                log.getOutcome(),
                log.getIpAddress(),
                log.getDetails(),
                log.getCreatedAt());
    }
}
