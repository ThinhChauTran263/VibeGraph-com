package com.vibegraph.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vibegraph.infrastructure.config.InfrastructureMonitorProperties;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot;

class InfrastructureEventStreamTest {

    @Test
    void rejectsTheSixtyFifthSubscriberWithoutAddingItToTheSet() throws Exception {
        InfrastructureEventStream stream = newStream();
        Set<SseEmitter> emitters = emitters(stream);
        for (int i = 0; i < 64; i++) {
            stream.subscribe();
        }

        SseEmitter rejected = stream.subscribe();

        assertThat(emitters).hasSize(64).doesNotContain(rejected);
    }

    @Test
    void removesAnEmitterWhenItsTransportReportsAnIoFailure() throws Exception {
        InfrastructureEventStream stream = newStream();
        Set<SseEmitter> emitters = emitters(stream);
        SseEmitter broken = mock(SseEmitter.class);
        doThrow(new IOException("client disconnected"))
                .when(broken).send(any(SseEmitter.SseEventBuilder.class));
        emitters.add(broken);

        stream.publish(snapshot());

        verify(broken, timeout(1_000)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(emitters).doesNotContain(broken);
        stream.shutdownDeliveryExecutor();
    }

    @Test
    void alwaysPublishesTheSnapshotToTheWebsocketTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        InfrastructureEventStream stream = new InfrastructureEventStream(
                new InfrastructureMonitorProperties(), messaging);

        InfrastructureSnapshot snapshot = snapshot();
        stream.publish(snapshot);

        verify(messaging, timeout(1_000)).convertAndSend("/topic/admin/infrastructure", snapshot);
        stream.shutdownDeliveryExecutor();
    }

    @Test
    void slowSubscriberCannotBlockTheSamplerThread() throws Exception {
        InfrastructureEventStream stream = newStream();
        Set<SseEmitter> emitters = emitters(stream);
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        SseEmitter slow = mock(SseEmitter.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            entered.countDown();
            release.await(2, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        }).when(slow).send(any(SseEmitter.SseEventBuilder.class));
        emitters.add(slow);

        long started = System.nanoTime();
        stream.publish(snapshot());
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(elapsedMs).isLessThan(200);
        assertThat(entered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        release.countDown();
        verify(slow, timeout(1_000)).send(any(SseEmitter.SseEventBuilder.class));
        stream.shutdownDeliveryExecutor();
    }

    @SuppressWarnings("unchecked")
    private static Set<SseEmitter> emitters(InfrastructureEventStream stream) throws Exception {
        Field field = InfrastructureEventStream.class.getDeclaredField("emitters");
        field.setAccessible(true);
        return (Set<SseEmitter>) field.get(stream);
    }

    private static InfrastructureEventStream newStream() {
        InfrastructureMonitorProperties properties = new InfrastructureMonitorProperties();
        return new InfrastructureEventStream(properties, mock(SimpMessagingTemplate.class));
    }

    private static InfrastructureSnapshot snapshot() {
        return new InfrastructureSnapshot(Instant.parse("2026-08-25T00:00:00Z"), "HEALTHY",
                new InfrastructureSnapshot.HostMetrics(10, 4, 2.5, 10, 10, "test", "MEASURED"),
                new InfrastructureSnapshot.MemoryMetrics(1_000, 500, 500, 50, List.of(), "test", "MEASURED"),
                new InfrastructureSnapshot.DiskMetrics(1_000, 500, 500, 50, List.of(), "test", "MEASURED"),
                new InfrastructureSnapshot.NetworkMetrics(0, 0, 0, "test", "MEASURED"),
                new InfrastructureSnapshot.DiskIoMetrics(0, 0, 0d, "test", "MEASURED"),
                List.of(), null, null, List.of(), List.of());
    }
}
