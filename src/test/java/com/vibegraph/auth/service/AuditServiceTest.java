package com.vibegraph.auth.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private AuditLogWriter auditLogWriter;
    @Mock private CurrentUser currentUser;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(
                auditLogRepository,
                retentionRepository,
                auditLogWriter,
                Clock.fixed(NOW, ZoneOffset.UTC),
                currentUser);
    }

    @Test
    @DisplayName("record delegates to the independent audit writer")
    void record_delegatesToWriter() {
        Map<String, String> details = Map.of("password", "secret");

        service.record("FAILED_LOGIN", null, null, "USER", "unknown@test.local", "FAILURE", details);

        verify(auditLogWriter).write(
                "FAILED_LOGIN", null, null, "USER", "unknown@test.local", "FAILURE", null, details);
    }

    @Test
    @DisplayName("unauthenticated actions keep a null actor")
    void recordCurrentUser_unauthenticated_writesAnonymousAudit() {
        when(currentUser.id()).thenThrow(new com.vibegraph.common.exception.UnauthorizedException("missing"));

        service.recordCurrentUser("LOGOUT", null, "SESSION", null, Map.of());

        verify(auditLogWriter).write("LOGOUT", null, null, "SESSION", null, "SUCCESS", null, Map.of());
    }

    @Test
    @DisplayName("unexpected actor lookup failures are not hidden as anonymous audits")
    void recordCurrentUser_actorLookupFailure_propagates() {
        when(currentUser.id()).thenThrow(new IllegalStateException("principal store unavailable"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.recordCurrentUser("USER_BLOCK", null, "USER", "target", Map.of()));
    }

    @Test
    @DisplayName("retention updates are audited through the independent writer")
    void updateRetention_validValue_auditsChange() {
        java.util.UUID adminId = java.util.UUID.randomUUID();
        AuditRetentionSetting setting = AuditRetentionSetting.builder()
                .id(AuditRetentionSetting.SINGLETON_ID)
                .retentionDays(90)
                .build();
        when(retentionRepository.findById(AuditRetentionSetting.SINGLETON_ID))
                .thenReturn(Optional.of(setting));
        when(retentionRepository.save(setting)).thenReturn(setting);
        when(currentUser.id()).thenReturn(adminId);

        service.updateRetention(180);

        verify(auditLogWriter).write(
                "AUDIT_RETENTION_UPDATE", adminId, null, "AUDIT_RETENTION", null,
                "SUCCESS", null, Map.of("retentionDays", 180));
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
