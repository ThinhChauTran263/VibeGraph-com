package com.vibegraph.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.AuditRetentionSetting;
import com.vibegraph.auth.domain.AuditLog;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.dto.AuditLogResponse;
import com.vibegraph.auth.dto.AuditRetentionResponse;
import com.vibegraph.auth.repository.AuditLogSpecifications;
import com.vibegraph.auth.repository.AuditLogRepository;
import com.vibegraph.auth.repository.AuditRetentionSettingRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int DEFAULT_RETENTION_DAYS = 90;

    private final AuditLogRepository auditLogRepository;
    private final AuditRetentionSettingRepository retentionRepository;
    private final AuditLogWriter auditLogWriter;
    private final UserRepository userRepository;
    private final Clock clock;
    private final CurrentUser currentUser;

    public AuditLogResponse record(
            String action,
            UUID actorUserId,
            UUID targetUserId,
            String targetType,
            String targetId,
            String outcome,
            Map<String, ?> details) {
        return auditLogWriter.write(
                action, actorUserId, targetUserId, targetType, targetId, outcome, requestIp(), details);
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
        } catch (UnauthorizedException ignored) {
            // Authentication failures intentionally keep actor_user_id null.
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
        Page<AuditLog> logs = auditLogRepository.findAll(
                        AuditLogSpecifications.withFilters(
                                normalize(action), normalize(outcome), actorUserId, targetUserId, from, to),
                        safePageable);
        Map<UUID, String> names = userDisplayNames(logs);
        return logs.map(log -> AuditLogResponse.from(log).withUserDisplayNames(
                names.get(log.getActorUserId()),
                names.get(log.getTargetUserId())));
    }

    @Transactional(readOnly = true)
    public AuditLogResponse get(UUID id) {
        return auditLogRepository.findById(id)
                .map(log -> AuditLogResponse.from(log).withUserDisplayNames(
                        userDisplayName(log.getActorUserId()),
                        userDisplayName(log.getTargetUserId())))
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
        recordCurrentUser("AUDIT_RETENTION_UPDATE", null, "AUDIT_RETENTION", null,
                Map.of("retentionDays", retentionDays));
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
        String ip = request.getRemoteAddr();
        return ip == null ? null : ip.substring(0, Math.min(ip.length(), 64));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<UUID, String> userDisplayNames(Page<AuditLog> logs) {
        HashSet<UUID> ids = new HashSet<>();
        logs.getContent().forEach(log -> {
            if (log.getActorUserId() != null) {
                ids.add(log.getActorUserId());
            }
            if (log.getTargetUserId() != null) {
                ids.add(log.getTargetUserId());
            }
        });
        if (ids.isEmpty()) {
            return Map.of();
        }
        HashMap<UUID, String> names = new HashMap<>();
        userRepository.findAllById(ids).forEach(user -> names.put(user.getId(), displayName(user)));
        return names;
    }

    private String userDisplayName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(this::displayName)
                .orElse(null);
    }

    private String displayName(User user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getEmail()
                : user.getDisplayName();
    }

}
