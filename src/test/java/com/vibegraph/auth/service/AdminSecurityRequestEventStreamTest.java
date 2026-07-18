package com.vibegraph.auth.service;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vibegraph.abuse.RequestEventResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminSecurityRequestEventStreamTest {

    @Test
    void subscribe_WhenClientCompletes_RemovesSubscriber() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doNothing().when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        AdminSecurityRequestEventStream stream = new AdminSecurityRequestEventStream(() -> emitter);
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);

        stream.subscribe();
        verify(emitter).onCompletion(completion.capture());
        assertThat(stream.subscriberCount()).isOne();

        completion.getValue().run();

        assertThat(stream.subscriberCount()).isZero();
    }

    @Test
    void publish_DisconnectedEmitter_RemovesSubscriber() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doNothing().doThrow(new IOException("disconnected"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        AdminSecurityRequestEventStream stream = new AdminSecurityRequestEventStream(() -> emitter);
        stream.subscribe();

        stream.publish(new RequestEventResponse(
                UUID.randomUUID(), null, null, null, null, "203.0.113.10",
                "/api/projects", "GET", 200, "REQUEST", Instant.parse("2026-07-19T10:00:00Z")));

        assertThat(stream.subscriberCount()).isZero();
    }
}
