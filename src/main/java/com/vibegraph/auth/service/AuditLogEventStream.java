package com.vibegraph.auth.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vibegraph.auth.dto.AuditLogResponse;

/** Manages admin-only SSE subscribers for sanitized audit log events. */
@Service
public class AuditLogEventStream {

    private static final long EMITTER_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final Supplier<SseEmitter> emitterFactory;

    public AuditLogEventStream() {
        this(() -> new SseEmitter(EMITTER_TIMEOUT_MILLIS));
    }

    AuditLogEventStream(Supplier<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = emitterFactory.get();
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        emitters.add(emitter);
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("status", "connected")));
        } catch (IOException | IllegalStateException ex) {
            remove(emitter);
        }
        return emitter;
    }

    public void publish(AuditLogResponse event) {
        for (SseEmitter emitter : emitters) {
            try {
                SseEmitter.SseEventBuilder builder = SseEmitter.event()
                        .name("audit-log")
                        .data(event);
                if (event.id() != null) {
                    builder.id(event.id().toString());
                }
                emitter.send(builder);
            } catch (IOException | IllegalStateException ex) {
                remove(emitter);
            }
        }
    }

    public boolean hasSubscribers() {
        return !emitters.isEmpty();
    }

    int subscriberCount() {
        return emitters.size();
    }

    private void remove(SseEmitter emitter) {
        emitters.remove(emitter);
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // The emitter is already closed by the servlet container.
        }
    }
}
