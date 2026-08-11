package com.vibegraph.abuse;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.SecurityEvent;
import com.vibegraph.auth.repository.SecurityEventRepository;
import com.vibegraph.auth.service.AdminSecurityRequestEventPublisher;

import lombok.RequiredArgsConstructor;

/**
 * Writes one {@link TelemetryBatch} inside a single Supabase transaction.
 *
 * <p>A request event and the security event it produced form one logical pair and stay in the
 * same transaction, so a retry never persists half of a pair. Both inserts are idempotent on the
 * primary key, which is what makes retrying a partially applied batch safe.
 */
@Service
@RequiredArgsConstructor
public class RequestEventBatchWriter {

    private final RequestEventRepository requestEventRepository;
    private final SecurityEventRepository securityEventRepository;
    private final AdminSecurityRequestEventPublisher requestEventPublisher;

    @Transactional(transactionManager = "supabaseTransactionManager")
    public void write(TelemetryBatch batch) {
        List<RequestEvent> requestEvents = batch.events().stream()
                .map(PendingRequestEvent::requestEvent)
                .toList();
        List<SecurityEvent> securityEvents = batch.events().stream()
                .map(PendingRequestEvent::securityEvent)
                .filter(Objects::nonNull)
                .toList();
        requestEventRepository.saveAll(requestEvents);
        securityEventRepository.saveAll(securityEvents);
        requestEvents.forEach(requestEventPublisher::publishAfterCommit);
    }
}
