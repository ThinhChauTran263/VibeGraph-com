package com.vibegraph.abuse;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vibegraph.auth.repository.SecurityEventRepository;
import com.vibegraph.auth.service.AdminSecurityRequestEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestEventServiceTest {

    @Test
    void record_SavedRequestEvent_PublishesLiveUpdate() {
        RequestEventRepository requestEventRepository = mock(RequestEventRepository.class);
        SecurityEventRepository securityEventRepository = mock(SecurityEventRepository.class);
        AdminSecurityRequestEventPublisher publisher = mock(AdminSecurityRequestEventPublisher.class);
        RequestEvent saved = RequestEvent.builder()
                .id(UUID.randomUUID())
                .ipAddress("203.0.113.10")
                .route("/api/projects")
                .method("GET")
                .status(200)
                .eventType("REQUEST")
                .occurredAt(Instant.parse("2026-07-19T10:00:00Z"))
                .build();
        when(requestEventRepository.save(any(RequestEvent.class))).thenReturn(saved);
        RequestEventService service = new RequestEventService(
                requestEventRepository, securityEventRepository, publisher);

        service.record(null, null, saved.getIpAddress(), saved.getRoute(), saved.getMethod(),
                saved.getStatus(), saved.getOccurredAt(), saved.getEventType());

        verify(publisher).publishAfterCommit(saved);
    }
}
