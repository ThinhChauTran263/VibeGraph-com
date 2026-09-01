package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.entity.AuditLog;
import com.vibegraph.auth.repository.AuditLogRepository;
import com.vibegraph.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuditLogWriterTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditRedactor redactor;
    @Mock private AuditLogEventPublisher eventPublisher;

    private AuditLogWriter writer;

    @BeforeEach
    void setUp() {
        writer = new AuditLogWriter(auditLogRepository, userRepository, redactor, eventPublisher);
    }

    @Test
    void write_oversizedDetails_persistsValidTruncationMarker() {
        when(redactor.redact(any())).thenReturn("x".repeat(4001));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.write("LOGIN", null, null, "USER", "id", "SUCCESS", null, Map.of());

        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(log ->
                log.getDetails().equals("{\"truncated\":true}")));
    }

    @Test
    void write_persistsRedactedDetailsAndPublishesSavedProjection() {
        when(redactor.redact(any())).thenReturn("{\"password\":\"[REDACTED]\"}");
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            saved.setCreatedAt(Instant.parse("2026-07-19T10:00:00Z"));
            return saved;
        });

        var response = writer.write(
                "FAILED_LOGIN", null, null, "USER", "unknown@test.local", "FAILURE", "127.0.0.1",
                Map.of("password", "secret"));

        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(log ->
                log.getAction().equals("FAILED_LOGIN")
                        && log.getDetails().equals("{\"password\":\"[REDACTED]\"}")));
        verify(eventPublisher).publishAfterCommit(response);
        assertThat(response.details()).isEqualTo("{\"password\":\"[REDACTED]\"}");
    }
}
