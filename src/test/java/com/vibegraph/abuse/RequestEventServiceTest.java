package com.vibegraph.abuse;

import com.vibegraph.abuse.entity.RequestEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.common.supabase.SupabaseProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RequestEventServiceTest {

    private static final Instant START = Instant.parse("2026-07-19T10:00:00Z");

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("a recorded event is queued and written as one batch")
    void record_queuedRequestEvent_flushesBatch() {
        RecordingWriter writer = new RecordingWriter();
        RequestEventService service = service(new SupabaseProperties(), writer, new MutableClock(START));

        service.record(null, null, "203.0.113.10", "/api/projects", "GET", 200, START, "REQUEST");
        assertThat(service.queuedEvents()).isOne();
        service.flush();

        assertThat(writer.batches).singleElement().satisfies(batch -> {
            assertThat(batch.events()).singleElement().satisfies(pending -> {
                assertThat(pending.requestEvent().getIpAddress()).isEqualTo("203.0.113.10");
                assertThat(pending.requestEvent().getOccurredAt()).isEqualTo(START);
                assertThat(pending.securityEvent()).isNull();
            });
        });
        assertThat(service.queuedEvents()).isZero();
        assertThat(counter(RequestEventService.FLUSH_SUCCESS_METRIC)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a failed batch keeps its identity and its event ids across retries")
    void flush_failedBatch_keepsBatchAndEventIdentity() {
        MutableClock clock = new MutableClock(START);
        RecordingWriter writer = new RecordingWriter();
        writer.failWhile(batch -> writer.batches.size() <= 1);
        RequestEventService service = service(properties(telemetry -> {}), writer, clock);

        service.record(null, null, "203.0.113.10", "/api/projects", "GET", 200, START, "REQUEST");
        service.flush();
        clock.advance(Duration.ofSeconds(2));
        service.flush();

        assertThat(writer.batches).hasSize(2);
        TelemetryBatch first = writer.batches.get(0);
        TelemetryBatch second = writer.batches.get(1);
        assertThat(second.batchId()).isEqualTo(first.batchId());
        assertThat(second.requestEventIds()).isEqualTo(first.requestEventIds());
        assertThat(second.attempts()).isEqualTo(1);
        assertThat(counter(RequestEventService.RETRY_METRIC)).isEqualTo(1.0);
        assertThat(counter(RequestEventService.FLUSH_FAILURE_METRIC)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("retries wait for the exponential backoff to elapse")
    void flush_failedBatch_waitsForBackoff() {
        MutableClock clock = new MutableClock(START);
        RecordingWriter writer = new RecordingWriter();
        writer.failWhile(batch -> true);
        RequestEventService service = service(
                properties(telemetry -> telemetry.setRetryBackoffMs(5_000)), writer, clock);

        service.record(null, null, "203.0.113.10", "/api/projects", "GET", 200, START, "REQUEST");
        service.flush();
        assertThat(writer.batches).hasSize(1);

        service.flush();
        assertThat(writer.batches).as("not due yet").hasSize(1);

        clock.advance(Duration.ofSeconds(5));
        service.flush();
        assertThat(writer.batches).hasSize(2);

        // Second failure doubles the backoff.
        clock.advance(Duration.ofSeconds(5));
        service.flush();
        assertThat(writer.batches).as("backoff doubled to 10s").hasSize(2);
        clock.advance(Duration.ofSeconds(5));
        service.flush();
        assertThat(writer.batches).hasSize(3);
    }

    @Test
    @DisplayName("the retry queue is bounded and overflow is counted, never silent")
    void flush_retryQueueFull_abandonsBatchAndCountsDrop() {
        MutableClock clock = new MutableClock(START);
        RecordingWriter writer = new RecordingWriter();
        writer.failWhile(batch -> true);
        RequestEventService service = service(properties(telemetry -> {
            telemetry.setRetryQueueCapacity(1);
            telemetry.setBatchSize(1);
            telemetry.setMaxAttempts(10);
        }), writer, clock);

        service.record(null, null, "203.0.113.1", "/api/a", "GET", 200, START, "REQUEST");
        service.flush();
        service.record(null, null, "203.0.113.2", "/api/b", "GET", 200, START, "REQUEST");
        service.flush();

        assertThat(service.queuedRetryBatches()).isOne();
        assertThat(counter(RequestEventService.ABANDONED_METRIC)).isEqualTo(1.0);
        assertThat(counter(RequestEventService.DROPPED_METRIC)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("continuous retry traffic never starves fresh telemetry")
    void flush_saturatedRetryQueue_stillDrainsFreshEvents() {
        MutableClock clock = new MutableClock(START);
        PoisonWriter writer = new PoisonWriter("/api/poison");
        RequestEventService service = service(properties(telemetry -> {
            telemetry.setBatchSize(1);
            telemetry.setRetryBatchesPerCycle(1);
            telemetry.setMaxAttempts(50);
            telemetry.setRetryBackoffMs(100);
        }), writer, clock);

        for (int index = 0; index < 3; index++) {
            service.record(null, null, "203.0.113.1", "/api/poison", "GET", 200, START, "REQUEST");
            service.flush();
        }
        assertThat(service.queuedRetryBatches()).isEqualTo(3);

        service.record(null, null, "203.0.113.9", "/api/healthy", "GET", 200, START, "REQUEST");
        clock.advance(Duration.ofSeconds(1));
        service.flush();

        assertThat(writer.written).anySatisfy(event ->
                assertThat(event.getRoute()).isEqualTo("/api/healthy"));
    }

    @Test
    @DisplayName("a poison event is bisected out so the healthy events are still written")
    void flush_poisonEvent_isIsolatedAndAbandoned() {
        MutableClock clock = new MutableClock(START);
        PoisonWriter writer = new PoisonWriter("/api/poison");
        RequestEventService service = service(properties(telemetry -> {
            telemetry.setBatchSize(4);
            telemetry.setMaxAttempts(2);
            telemetry.setRetryBackoffMs(100);
            telemetry.setRetryBatchesPerCycle(8);
            telemetry.setMaxSplitDepth(4);
        }), writer, clock);

        service.record(null, null, "203.0.113.1", "/api/ok-1", "GET", 200, START, "REQUEST");
        service.record(null, null, "203.0.113.2", "/api/ok-2", "GET", 200, START, "REQUEST");
        service.record(null, null, "203.0.113.3", "/api/poison", "GET", 200, START, "REQUEST");
        service.record(null, null, "203.0.113.4", "/api/ok-3", "GET", 200, START, "REQUEST");

        for (int cycle = 0; cycle < 30 && service.queuedRetryBatches() > 0 || cycle == 0; cycle++) {
            service.flush();
            clock.advance(Duration.ofSeconds(1));
        }

        assertThat(writer.written).extracting(RequestEvent::getRoute)
                .contains("/api/ok-1", "/api/ok-2", "/api/ok-3")
                .doesNotContain("/api/poison");
        assertThat(service.queuedRetryBatches()).isZero();
        assertThat(counter(RequestEventService.POISON_METRIC)).isEqualTo(1.0);
        assertThat(counter(RequestEventService.ABANDONED_METRIC)).isEqualTo(1.0);
        assertThat(counter(RequestEventService.DROPPED_METRIC)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("bisecting stops at the configured split depth")
    void flush_splitDepthZero_abandonsWholeBatchWithoutSplitting() {
        MutableClock clock = new MutableClock(START);
        PoisonWriter writer = new PoisonWriter("/api/poison");
        RequestEventService service = service(properties(telemetry -> {
            telemetry.setBatchSize(4);
            telemetry.setMaxAttempts(2);
            telemetry.setRetryBackoffMs(100);
            telemetry.setMaxSplitDepth(0);
        }), writer, clock);

        service.record(null, null, "203.0.113.1", "/api/ok-1", "GET", 200, START, "REQUEST");
        service.record(null, null, "203.0.113.3", "/api/poison", "GET", 200, START, "REQUEST");

        for (int cycle = 0; cycle < 10; cycle++) {
            service.flush();
            clock.advance(Duration.ofSeconds(1));
        }

        assertThat(service.queuedRetryBatches()).isZero();
        assertThat(counter(RequestEventService.ABANDONED_METRIC)).isEqualTo(1.0);
        assertThat(counter(RequestEventService.DROPPED_METRIC)).isEqualTo(2.0);
        assertThat(counter(RequestEventService.POISON_METRIC)).isZero();
    }

    @Test
    @DisplayName("a full queue drops the oldest event and counts it, including security events")
    void record_queueOverflow_countsDroppedEvents() {
        RecordingWriter writer = new RecordingWriter();
        RequestEventService service = service(
                properties(telemetry -> telemetry.setQueueCapacity(100)), writer, new MutableClock(START));

        for (int index = 0; index < 102; index++) {
            service.record(null, null, "203.0.113.1", "/api/a", "GET", 429, START, "RATE_LIMIT");
        }

        assertThat(service.queuedEvents()).isEqualTo(100);
        assertThat(counter(RequestEventService.DROPPED_METRIC)).isEqualTo(2.0);
        assertThat(counter(RequestEventService.SECURITY_DROPPED_METRIC)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("B-L8: a full queue of plain telemetry keeps an incoming security event, not the oldest byte")
    void record_queueFullOfPlainEvents_securityEventSurvives() {
        RecordingWriter writer = new RecordingWriter();
        RequestEventService service = service(
                properties(telemetry -> telemetry.setQueueCapacity(100)), writer, new MutableClock(START));

        // Fill the queue entirely with NON-security events.
        for (int index = 0; index < 100; index++) {
            service.record(null, null, "203.0.113.1", "/api/a", "GET", 200, START, "REQUEST");
        }
        double droppedBefore = counter(RequestEventService.DROPPED_METRIC);
        double securityDroppedBefore = counter(RequestEventService.SECURITY_DROPPED_METRIC);

        // Now the queue is full and a security event arrives.
        service.record(null, null, "203.0.113.1", "/api/a", "GET", 429, START, "RATE_LIMIT");

        assertThat(service.queuedEvents()).isEqualTo(100);
        assertThat(service.hasQueuedSecurityEvent())
                .as("the security event must have displaced a plain event, not been dropped")
                .isTrue();
        assertThat(counter(RequestEventService.DROPPED_METRIC)).isEqualTo(droppedBefore + 1);
        assertThat(counter(RequestEventService.SECURITY_DROPPED_METRIC))
                .as("no security event may be lost when plain telemetry is available to shed")
                .isEqualTo(securityDroppedBefore);
    }

    @Test
    @DisplayName("a concurrent flush is skipped rather than overlapping")
    void flush_reentrantCall_isSkippedAndCounted() {
        RecordingWriter writer = new RecordingWriter();
        RequestEventService service = service(new SupabaseProperties(), writer, new MutableClock(START));
        writer.onWrite(batch -> service.flush());

        service.record(null, null, "203.0.113.1", "/api/a", "GET", 200, START, "REQUEST");
        service.flush();

        assertThat(counter(RequestEventService.CONCURRENT_SKIPPED_METRIC)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("shutdown drains more events than fit in one batch")
    void drainBeforeShutdown_largeQueue_drainsAcrossMultipleBatches() {
        RecordingWriter writer = new RecordingWriter();
        RequestEventService service = service(properties(telemetry -> {
            telemetry.setBatchSize(1);
            telemetry.setShutdownDrainTimeoutMs(10_000);
        }), writer, Clock.systemUTC());

        for (int index = 0; index < 3; index++) {
            service.record(null, null, "203.0.113." + index, "/api/a", "GET", 200, START, "REQUEST");
        }
        service.drainBeforeShutdown();

        assertThat(writer.batches).hasSize(3);
        assertThat(service.queuedEvents()).isZero();
    }

    @Test
    @DisplayName("shutdown stops at its deadline when the writer keeps failing")
    void drainBeforeShutdown_failingWriter_stopsAtDeadlineAndCountsLoss() {
        RecordingWriter writer = new RecordingWriter();
        writer.failWhile(batch -> true);
        RequestEventService service = service(properties(telemetry -> {
            telemetry.setBatchSize(1);
            telemetry.setMaxAttempts(50);
            telemetry.setRetryBackoffMs(100);
            telemetry.setShutdownDrainTimeoutMs(300);
            telemetry.setShutdownRetryPauseMs(50);
        }), writer, Clock.systemUTC());

        service.record(null, null, "203.0.113.1", "/api/a", "GET", 429, START, "RATE_LIMIT");

        long startedAt = System.nanoTime();
        service.drainBeforeShutdown();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(elapsedMs).isLessThan(5_000L);
        assertThat(service.queuedEvents()).isZero();
        assertThat(service.queuedRetryBatches()).isZero();
        assertThat(counter(RequestEventService.DROPPED_METRIC)).isPositive();
        assertThat(counter(RequestEventService.SECURITY_DROPPED_METRIC)).isPositive();
    }

    @Test
    @DisplayName("recording after shutdown is counted, never thrown back at the request thread")
    void record_afterShutdown_dropsWithoutThrowing() {
        RecordingWriter writer = new RecordingWriter();
        RequestEventService service = service(new SupabaseProperties(), writer, Clock.systemUTC());
        service.drainBeforeShutdown();

        assertThatCode(() -> service.record(null, null, "203.0.113.1", "/api/a", "GET",
                200, START, "REQUEST")).doesNotThrowAnyException();

        assertThat(service.queuedEvents()).isZero();
        assertThat(counter(RequestEventService.DROPPED_METRIC)).isEqualTo(1.0);
    }

    private RequestEventService service(SupabaseProperties properties,
            RequestEventBatchWriter writer, Clock clock) {
        return new RequestEventService(writer, properties, meterRegistry, clock);
    }

    private SupabaseProperties properties(java.util.function.Consumer<SupabaseProperties.Telemetry> customizer) {
        SupabaseProperties properties = new SupabaseProperties();
        customizer.accept(properties.getTelemetry());
        return properties;
    }

    private double counter(String name) {
        return meterRegistry.counter(name).count();
    }

    /** Batch writer stub that records every attempt and can be told when to fail. */
    private static class RecordingWriter extends RequestEventBatchWriter {

        private final List<TelemetryBatch> batches = new ArrayList<>();
        private Predicate<TelemetryBatch> failWhen = batch -> false;
        private java.util.function.Consumer<TelemetryBatch> onWrite = batch -> { };

        RecordingWriter() {
            super(null, null, null);
        }

        void failWhile(Predicate<TelemetryBatch> predicate) {
            this.failWhen = predicate;
        }

        void onWrite(java.util.function.Consumer<TelemetryBatch> callback) {
            this.onWrite = callback;
        }

        @Override
        public void write(TelemetryBatch batch) {
            batches.add(batch);
            onWrite.accept(batch);
            if (failWhen.test(batch)) {
                throw new IllegalStateException("simulated Supabase failure");
            }
        }
    }

    /** Batch writer stub that fails for any batch containing a specific route. */
    private static class PoisonWriter extends RequestEventBatchWriter {

        private final String poisonRoute;
        private final List<RequestEvent> written = new ArrayList<>();
        private final AtomicInteger attempts = new AtomicInteger();

        PoisonWriter(String poisonRoute) {
            super(null, null, null);
            this.poisonRoute = poisonRoute;
        }

        @Override
        public void write(TelemetryBatch batch) {
            attempts.incrementAndGet();
            boolean poisoned = batch.events().stream()
                    .anyMatch(event -> poisonRoute.equals(event.requestEvent().getRoute()));
            if (poisoned) {
                throw new IllegalStateException("simulated constraint violation");
            }
            batch.events().forEach(event -> written.add(event.requestEvent()));
        }
    }

    @SuppressWarnings("unused")
    private static UUID anyId() {
        return UUID.randomUUID();
    }
}
