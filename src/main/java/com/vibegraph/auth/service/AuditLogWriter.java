package com.vibegraph.auth.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.AuditLog;
import com.vibegraph.auth.dto.AuditLogResponse;
import com.vibegraph.auth.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

/** Persists audit records independently from the business transaction. */
@Service
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;
    private final AuditRedactor redactor;
    private final AuditLogEventPublisher auditLogEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogResponse write(
            String action,
            UUID actorUserId,
            UUID targetUserId,
            String targetType,
            String targetId,
            String outcome,
            String ipAddress,
            Map<String, ?> details) {
        String redactedDetails = validDetails(redactor.redact(details == null ? Map.of() : details));
        AuditLog saved = auditLogRepository.save(AuditLog.builder()
                .action(action)
                .actorUserId(actorUserId)
                .targetUserId(targetUserId)
                .targetType(targetType)
                .targetId(limit(targetId, 160))
                .outcome(outcome)
                .ipAddress(limit(ipAddress, 64))
                .details(redactedDetails)
                .build());
        AuditLogResponse response = AuditLogResponse.from(saved);
        auditLogEventPublisher.publishAfterCommit(response);
        return response;
    }

    private String validDetails(String redactedDetails) {
        if (redactedDetails.length() <= 4000) {
            return redactedDetails;
        }
        return "{\"truncated\":true}";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
