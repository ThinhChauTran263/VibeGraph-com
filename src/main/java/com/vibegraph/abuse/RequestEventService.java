package com.vibegraph.abuse;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.SecurityEvent;
import com.vibegraph.auth.repository.SecurityEventRepository;
import com.vibegraph.auth.service.AdminSecurityRequestEventPublisher;

@Service
public class RequestEventService {

    private final RequestEventRepository requestEventRepository;
    private final SecurityEventRepository securityEventRepository;
    private final AdminSecurityRequestEventPublisher requestEventPublisher;

    public RequestEventService(RequestEventRepository requestEventRepository,
            SecurityEventRepository securityEventRepository,
            AdminSecurityRequestEventPublisher requestEventPublisher) {
        this.requestEventRepository = requestEventRepository;
        this.securityEventRepository = securityEventRepository;
        this.requestEventPublisher = requestEventPublisher;
    }

    @Transactional
    public void record(UUID userId, String apiKeyRef, String ipAddress, String route,
            String method, int status, Instant timestamp, String eventType) {
        try {
            RequestEvent saved = requestEventRepository.save(RequestEvent.builder()
                    .userId(userId)
                    .apiKeyRef(apiKeyRef)
                    .ipAddress(ipAddress)
                    .route(route)
                    .method(method)
                    .status(status)
                    .eventType(eventType)
                    .occurredAt(timestamp)
                    .build());
            requestEventPublisher.publishAfterCommit(saved);
            if ("RATE_LIMIT".equals(eventType)) {
                securityEventRepository.save(SecurityEvent.builder()
                        .eventType("RATE_LIMIT")
                        .severity("WARNING")
                        .subjectUserId(userId)
                        .apiKeyRef(apiKeyRef)
                        .source("HTTP")
                        .description("Request rate limit exceeded")
                        .build());
            }
        } catch (RuntimeException ignored) {
            // Abuse telemetry must never turn a healthy request into a 500.
        }
    }
}
