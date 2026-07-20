package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.vibegraph.auth.dto.AuditLogResponse;

class AuditLogEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishAfterCommit_OnlyPublishesAfterCommitAndOmitsDetails() {
        AuditLogEventStream stream = mock(AuditLogEventStream.class);
        when(stream.hasSubscribers()).thenReturn(true);
        AuditLogEventPublisher publisher = new AuditLogEventPublisher(stream, Clock.fixed(NOW, ZoneOffset.UTC));
        AuditLogResponse event = new AuditLogResponse(
                UUID.randomUUID(), "API_KEY_CREATE", null, null, null, null, "API_KEY", "key-1", "SUCCESS",
                "127.0.0.1", "{\"secret\":\"raw\"}", null);

        TransactionSynchronizationManager.initSynchronization();
        publisher.publishAfterCommit(event);

        verifyNoInteractions(stream);
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();

        ArgumentCaptor<AuditLogResponse> payload = ArgumentCaptor.forClass(AuditLogResponse.class);
        verify(stream).publish(payload.capture());
        assertThat(payload.getValue().details()).isNull();
        assertThat(payload.getValue().targetId()).isEqualTo("key-1");
        assertThat(payload.getValue().createdAt()).isEqualTo(NOW);
    }
}
