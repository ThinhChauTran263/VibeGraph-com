package com.vibegraph.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.AuditLog;
import com.vibegraph.auth.domain.AuditRetentionSetting;
import com.vibegraph.auth.dto.AuditLogResponse;
import com.vibegraph.auth.dto.AuditRetentionResponse;
import com.vibegraph.auth.repository.AuditLogSpecifications;
import com.vibegraph.auth.repository.AuditLogRepository;
import com.vibegraph.auth.repository.AuditRetentionSettingRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int DEFAULT_RETENTION_DAYS = 90;

    private final AuditLogRepository auditLogRepository;
    private final AuditRetentionSettingRepository retentionRepository;
    private final AuditRedactor redactor;
    private final Clock clock;
    private final CurrentUser currentUser;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogResponse record(
            String action,
            UUID actorUserId,
            UUID targetUserId,
            String targetType,
            String targetId,
            String outcome,
            Map<String, ?> details) {
        String redactedDetails = redactor.redact(details == null ? Map.of() : details);
        AuditLog saved = auditLogRepository.save(AuditLog.builder()
                .action(action)
                .actorUserId(actorUserId)
                .targetUserId(targetUserId)
                .targetType(targetType)
                .targetId(limit(targetId, 160))
                .outcome(outcome)
                .ipAddress(requestIp())
                .details(limit(redactedDetails, 4000))
                .build());
        return AuditLogResponse.from(saved);
    }

    public AuditLogResponse recordCurrentUser(
            String action,
            UUID targetUserId,
            String targetType,
            String targetId,
            Map<String, ?> details) {
        UUID actor = null;
        try {
            actor = currentUser.id();
        } catch (RuntimeException ignored) {
            // Anonymous actions intentionally keep actor_user_id null.
        }
        return record(action, actor, targetUserId, targetType, targetId, "SUCCESS", details);
    }

    public AuditLogResponse recordIpChange(String ipAddress, boolean blocked, Map<String, ?> details) {
        return recordCurrentUser(
                blocked ? "IP_BLOCK" : "IP_UNBLOCK", null, "IP_ADDRESS", ipAddress, details);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(
            String action,
            String outcome,
            UUID actorUserId,
            UUID targetUserId,
            Instant from,
            Instant to,
            Pageable pageable) {
        Pageable safePageable = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.min(Math.max(1, pageable.getPageSize()), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.findAll(
                        AuditLogSpecifications.withFilters(
                                normalize(action), normalize(outcome), actorUserId, targetUserId, from, to),
                        safePageable)
                .map(AuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public AuditLogResponse get(UUID id) {
        return auditLogRepository.findById(id)
                .map(AuditLogResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Audit log not found"));
    }

    @Transactional(readOnly = true)
    public AuditRetentionResponse getRetention() {
        return retentionRepository.findById(AuditRetentionSetting.SINGLETON_ID)
                .map(setting -> new AuditRetentionResponse(
                        setting.getRetentionDays(), setting.getUpdatedBy(), setting.getUpdatedAt()))
                .orElse(new AuditRetentionResponse(DEFAULT_RETENTION_DAYS, null, null));
    }

    @Transactional
    public AuditRetentionResponse updateRetention(int retentionDays) {
        if (retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalArgumentException("Retention must be between 1 and 3650 days");
        }
        AuditRetentionSetting setting = retentionRepository.findById(AuditRetentionSetting.SINGLETON_ID)
                .orElseGet(() -> AuditRetentionSetting.builder()
                        .id(AuditRetentionSetting.SINGLETON_ID)
                        .retentionDays(DEFAULT_RETENTION_DAYS)
                        .build());
        setting.setRetentionDays(retentionDays);
        setting.setUpdatedBy(currentUser.id());
        setting.setUpdatedAt(Instant.now(clock));
        AuditRetentionSetting saved = retentionRepository.save(setting);
        return new AuditRetentionResponse(saved.getRetentionDays(), saved.getUpdatedBy(), saved.getUpdatedAt());
    }

    @Transactional
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 2 * * ?")
    public void cleanupExpired() {
        int retentionDays = getRetention().retentionDays();
        auditLogRepository.deleteByCreatedAtBefore(Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS));
    }

    private String requestIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
        return ip == null ? null : ip.substring(0, Math.min(ip.length(), 64));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
