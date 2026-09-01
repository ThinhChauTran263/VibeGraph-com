package com.vibegraph.auth.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vibegraph.abuse.entity.RequestEvent;
import com.vibegraph.abuse.RequestEventResponse;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminSecurityRequestEventPublisherTest {

    @Test
    void publishAfterCommit_NoSubscribers_SkipsIdentityLookup() {
        UserRepository userRepository = mock(UserRepository.class);
        AdminSecurityRequestEventStream stream = mock(AdminSecurityRequestEventStream.class);
        RequestEvent event = RequestEvent.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .ipAddress("203.0.113.10")
                .route("/api/projects")
                .method("GET")
                .status(200)
                .eventType("REQUEST")
                .occurredAt(Instant.parse("2026-07-19T10:00:00Z"))
                .build();
        AdminSecurityRequestEventPublisher publisher =
                new AdminSecurityRequestEventPublisher(userRepository, stream);

        publisher.publishAfterCommit(event);

        verifyNoInteractions(userRepository);
    }

    @Test
    void publishAfterCommit_SavedEvent_PublishesSafeIdentityPayload() {
        UUID userId = UUID.randomUUID();
        UserRepository userRepository = mock(UserRepository.class);
        AdminSecurityRequestEventStream stream = mock(AdminSecurityRequestEventStream.class);
        when(stream.hasSubscribers()).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId)
                .displayName("Alice")
                .email("alice@example.com")
                .build()));
        RequestEvent event = RequestEvent.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .apiKeyRef("key-id:vbg_rawsupersecretvalue")
                .ipAddress("203.0.113.10")
                .route("/mcp")
                .method("POST")
                .status(200)
                .eventType("REQUEST")
                .occurredAt(Instant.parse("2026-07-19T10:00:00Z"))
                .build();
        AdminSecurityRequestEventPublisher publisher =
                new AdminSecurityRequestEventPublisher(userRepository, stream);

        publisher.publishAfterCommit(event);

        ArgumentCaptor<RequestEventResponse> payload = ArgumentCaptor.forClass(RequestEventResponse.class);
        verify(stream).publish(payload.capture());
        assertThat(payload.getValue().userDisplayName()).isEqualTo("Alice");
        assertThat(payload.getValue().userEmail()).isEqualTo("alice@example.com");
        assertThat(payload.getValue().apiKeyRef()).isEqualTo("vbg_raws****");
        assertThat(payload.getValue().apiKeyRef()).doesNotContain("supersecretvalue");
    }
}
