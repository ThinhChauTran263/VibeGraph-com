package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.AuditLog;
import com.vibegraph.auth.domain.AuditRetentionSetting;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.repository.AuditLogRepository;
import com.vibegraph.auth.repository.AuditRetentionSettingRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService")
class AuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditRetentionSettingRepository retentionRepository;
    @Mock private AuditRedactor redactor;
    @Mock private CurrentUser currentUser;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(
                auditLogRepository,
                retentionRepository,
                redactor,
                Clock.fixed(NOW, ZoneOffset.UTC),
                currentUser);
    }

    @Test
    @DisplayName("record persists only redacted details")
    void record_usesRedactedDetails() {
        when(redactor.redact(any())).thenReturn("{\"password\":\"[REDACTED]\"}");
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(
                "FAILED_LOGIN",
                null,
                null,
                "USER",
                "unknown@test.local",
                "FAILURE",
                Map.of("password", "secret"));

        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(log ->
                log.getAction().equals("FAILED_LOGIN")
                        && log.getDetails().equals("{\"password\":\"[REDACTED]\"}")));
    }

    @Test
    @DisplayName("cleanup uses the configured retention cutoff and defaults to 90 days")
    void cleanupExpired_usesConfiguredRetention() {
        when(retentionRepository.findById(AuditRetentionSetting.SINGLETON_ID))
                .thenReturn(Optional.of(AuditRetentionSetting.builder()
                        .id(AuditRetentionSetting.SINGLETON_ID)
                        .retentionDays(90)
                        .build()));

        service.cleanupExpired();

        verify(auditLogRepository).deleteByCreatedAtBefore(NOW.minusSeconds(90L * 24 * 60 * 60));
    }
}
