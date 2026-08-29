package com.vibegraph.infrastructure.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.vibegraph.infrastructure.dto.InfrastructureSnapshot;

import lombok.RequiredArgsConstructor;

/** Admin-only SSE stream for bounded infrastructure snapshots. */
@org.springframework.stereotype.Service
public class InfrastructureEventStream {

    private final com.vibegraph.infrastructure.config.InfrastructureMonitorProperties properties;
    private final SimpMessagingTemplate messagingTemplate;
    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final AtomicInteger subscriberCount = new AtomicInteger();
    private final AtomicReference<InfrastructureSnapshot> pendingSnapshot = new AtomicReference<>();
    private final AtomicBoolean dispatching = new AtomicBoolean();
    private final ExecutorService deliveryExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "infrastructure-stream-delivery");
        thread.setDaemon(true);
        return thread;
    });
    private static final int MAX_SUBSCRIBERS = 64;

    public InfrastructureEventStream(
            com.vibegraph.infrastructure.config.InfrastructureMonitorProperties properties,
            SimpMessagingTemplate messagingTemplate) {
        this.properties = properties;
        this.messagingTemplate = messagingTemplate;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMs());
        if (!reserveSubscriber()) {
            emitter.completeWithError(new IllegalStateException("Infrastructure stream subscriber limit reached"));
            return emitter;
        }
        emitters.add(emitter);
        Runnable cleanup = () -> removeEmitter(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("status", "connected")));
        } catch (IOException | IllegalStateException ex) {
            removeEmitter(emitter);
        }
        return emitter;
    }

    public void publish(InfrastructureSnapshot snapshot) {
        if (snapshot == null) return;
        pendingSnapshot.set(snapshot);
        scheduleDelivery();
    }

    private void scheduleDelivery() {
        if (!dispatching.compareAndSet(false, true)) return;
        try {
            deliveryExecutor.execute(this::drainLatestSnapshots);
        } catch (RejectedExecutionException ex) {
            dispatching.set(false);
        }
    }

    private void drainLatestSnapshots() {
        try {
            InfrastructureSnapshot snapshot;
            while ((snapshot = pendingSnapshot.getAndSet(null)) != null) deliver(snapshot);
        } finally {
            dispatching.set(false);
            if (pendingSnapshot.get() != null) scheduleDelivery();
        }
    }

    private void deliver(InfrastructureSnapshot snapshot) {
        try {
            messagingTemplate.convertAndSend("/topic/admin/infrastructure", snapshot);
        } catch (RuntimeException ignored) {
            // Realtime delivery is best-effort; sampler health must not depend on the broker.
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("infrastructure-snapshot").data(snapshot));
            } catch (IOException | IllegalStateException ex) {
                removeEmitter(emitter);
            }
        }
    }

    @PreDestroy
    void shutdownDeliveryExecutor() {
        deliveryExecutor.shutdownNow();
    }

    private boolean reserveSubscriber() {
        while (true) {
            int current = subscriberCount.get();
            if (current >= MAX_SUBSCRIBERS) return false;
            if (subscriberCount.compareAndSet(current, current + 1)) return true;
        }
    }

    private void removeEmitter(SseEmitter emitter) {
        if (emitters.remove(emitter)) subscriberCount.decrementAndGet();
    }
}
