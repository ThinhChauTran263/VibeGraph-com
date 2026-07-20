package com.vibegraph.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.vibegraph.auth.dto.AuditLogResponse;

import lombok.RequiredArgsConstructor;

/** Publishes sanitized audit log events after their database transaction commits. */
@Service
@RequiredArgsConstructor
public class AuditLogEventPublisher {

    private final AuditLogEventStream eventStream;
    private final Clock clock;

    public void publishAfterCommit(AuditLogResponse event) {
        Runnable publish = () -> {
            if (eventStream.hasSubscribers()) {
                eventStream.publish(safePayload(event));
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

    private AuditLogResponse safePayload(AuditLogResponse event) {
        Instant createdAt = event.createdAt() == null ? Instant.now(clock) : event.createdAt();
        return new AuditLogResponse(
                event.id(),
                event.action(),
                event.actorUserId(),
                event.actorDisplayName(),
                event.targetUserId(),
                event.targetUserDisplayName(),
                event.targetType(),
                event.targetId(),
                event.outcome(),
                event.ipAddress(),
                null,
                createdAt);
    }
}
