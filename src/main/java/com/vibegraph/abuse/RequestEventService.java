package com.vibegraph.abuse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.vibegraph.auth.domain.SecurityEvent;
import com.vibegraph.common.supabase.SupabaseProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Buffers request telemetry and writes it in batches.
 *
 * <p>Two queues are kept. Fresh events accumulate in a bounded queue and are drained into a
 * {@link TelemetryBatch} with a stable identity. A batch that fails moves to a separate bounded
 * retry queue with exponential backoff; it never dissolves back into the fresh queue, so a failing
 * batch cannot reorder or duplicate live traffic. Each flush cycle spends a bounded quota on
 * retries and then always drains fresh work, so neither side starves the other.
 *
 * <p>This is best-effort telemetry. Events are held in memory, a full queue drops the oldest
 * event, a batch that exhausts its attempts is abandoned, and a process crash loses whatever has
 * not been written. It is not an audit-grade or zero-loss pipeline.
 */
@Service
@Slf4j
public class RequestEventService {

    static final String FRESH_QUEUE_METRIC = "request_events.queue.fresh.size";
    static final String RETRY_QUEUE_METRIC = "request_events.queue.retry.size";
    static final String DROPPED_METRIC = "request_events.dropped.total";
    static final String FLUSH_SUCCESS_METRIC = "request_events.flush.success";
    static final String FLUSH_FAILURE_METRIC = "request_events.flush.failure";
    static final String FLUSH_LATENCY_METRIC = "request_events.flush.latency";
    static final String BATCH_SIZE_METRIC = "request_events.batch.size";
    static final String RETRY_METRIC = "request_events.retry.total";
    static final String ABANDONED_METRIC = "request_events.batch.abandoned";
    static final String POISON_METRIC = "request_events.poison.total";
    static final String CONCURRENT_SKIPPED_METRIC = "request_events.flush.concurrent_skipped";
    static final String SECURITY_DROPPED_METRIC = "security_events.dropped.total";
    static final String DRAIN_CEILING_METRIC = "request_events.drain.ceiling_per_second";

    private final RequestEventBatchWriter batchWriter;
    private final SupabaseProperties properties;
    private final Clock clock;

    private final ArrayBlockingQueue<PendingRequestEvent> freshQueue;
    private final LinkedBlockingDeque<TelemetryBatch> retryQueue;
    private final AtomicBoolean flushInProgress = new AtomicBoolean();
    private final AtomicBoolean acceptingEvents = new AtomicBoolean(true);

    private final Counter dropped;
    private final Counter securityDropped;
    private final Counter flushSuccess;
    private final Counter flushFailure;
    private final Counter retries;
    private final Counter abandoned;
    private final Counter poison;
    private final Counter concurrentSkipped;
    private final Timer flushLatency;
    private final DistributionSummary batchSize;

    public RequestEventService(RequestEventBatchWriter batchWriter, SupabaseProperties properties,
            MeterRegistry meterRegistry, Clock clock) {
        this.batchWriter = batchWriter;
        this.properties = properties;
        this.clock = clock;
        SupabaseProperties.Telemetry telemetry = properties.getTelemetry();
        this.freshQueue = new ArrayBlockingQueue<>(telemetry.getQueueCapacity());
        this.retryQueue = new LinkedBlockingDeque<>(telemetry.getRetryQueueCapacity());
        this.dropped = meterRegistry.counter(DROPPED_METRIC);
        this.securityDropped = meterRegistry.counter(SECURITY_DROPPED_METRIC);
        this.flushSuccess = meterRegistry.counter(FLUSH_SUCCESS_METRIC);
        this.flushFailure = meterRegistry.counter(FLUSH_FAILURE_METRIC);
        this.retries = meterRegistry.counter(RETRY_METRIC);
        this.abandoned = meterRegistry.counter(ABANDONED_METRIC);
        this.poison = meterRegistry.counter(POISON_METRIC);
        this.concurrentSkipped = meterRegistry.counter(CONCURRENT_SKIPPED_METRIC);
        this.flushLatency = meterRegistry.timer(FLUSH_LATENCY_METRIC);
        this.batchSize = DistributionSummary.builder(BATCH_SIZE_METRIC).register(meterRegistry);
        meterRegistry.gauge(FRESH_QUEUE_METRIC, this.freshQueue, ArrayBlockingQueue::size);
        meterRegistry.gauge(RETRY_QUEUE_METRIC, this.retryQueue, LinkedBlockingDeque::size);
        meterRegistry.gauge(DRAIN_CEILING_METRIC, drainCeilingPerSecond(telemetry));
        logDrainCeiling(telemetry);
    }

    /**
     * Events per second this instance can persist with the current configuration. Sustained arrival
     * above it fills the queue and sheds the oldest events, so it is worth stating at startup rather
     * than discovering from a drop counter.
     */
    static long drainCeilingPerSecond(SupabaseProperties.Telemetry telemetry) {
        long perCycle = (long) telemetry.getBatchSize() * telemetry.getFreshBatchesPerCycle();
        return perCycle * 1000L / Math.max(telemetry.getFlushIntervalMs(), 1L);
    }

    private void logDrainCeiling(SupabaseProperties.Telemetry telemetry) {
        long ceiling = drainCeilingPerSecond(telemetry);
        log.info("Telemetry drain ceiling is ~{} events/second per instance "
                        + "(batch-size {} x fresh-batches-per-cycle {} every {}ms). "
                        + "Sustained arrival above this fills the {}-event queue and sheds the oldest "
                        + "events; see VibeGraph-specs-2month/supabase-capacity-policy.md.",
                ceiling, telemetry.getBatchSize(), telemetry.getFreshBatchesPerCycle(),
                telemetry.getFlushIntervalMs(), telemetry.getQueueCapacity());
    }

    public void record(UUID userId, String apiKeyRef, String ipAddress, String route,
            String method, int status, Instant timestamp, String eventType) {
        // Normalize at the queue boundary so a malformed request can never widen a column and
        // fail an entire batch insert downstream.
        String normalizedApiKeyRef = RequestTelemetryNormalizer.normalizeApiKeyRef(apiKeyRef);
        String normalizedEventType = RequestTelemetryNormalizer.normalizeEventType(eventType);
        RequestEvent requestEvent = RequestEvent.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .apiKeyRef(normalizedApiKeyRef)
                .ipAddress(RequestTelemetryNormalizer.normalizeIpAddress(ipAddress))
                .route(RequestTelemetryNormalizer.normalizeRoute(route))
                .method(RequestTelemetryNormalizer.normalizeMethod(method))
                .status(status)
                .eventType(normalizedEventType)
                .occurredAt(timestamp)
                .build();
        SecurityEvent securityEvent = "RATE_LIMIT".equals(normalizedEventType)
                ? rateLimitEvent(userId, normalizedApiKeyRef)
                : null;
        offer(new PendingRequestEvent(requestEvent, securityEvent));
    }

    @Scheduled(fixedDelayString = "${vibegraph.supabase.telemetry.flush-interval-ms:2000}")
    public void flush() {
        if (!flushInProgress.compareAndSet(false, true)) {
            concurrentSkipped.increment();
            return;
        }
        try {
            processDueRetries();
            processFreshQueue();
        } finally {
            flushInProgress.set(false);
        }
    }

    /**
     * Stops accepting new events, then drains both queues until they are empty or the configured
     * deadline passes. Whatever is still queued when the deadline is reached is counted and logged
     * rather than silently discarded.
     */
    @PreDestroy
    public void drainBeforeShutdown() {
        acceptingEvents.set(false);
        SupabaseProperties.Telemetry telemetry = properties.getTelemetry();
        Instant deadline = Instant.now(clock).plusMillis(telemetry.getShutdownDrainTimeoutMs());
        while (Instant.now(clock).isBefore(deadline) && !(freshQueue.isEmpty() && retryQueue.isEmpty())) {
            if (!flushInProgress.compareAndSet(false, true)) {
                pauseBriefly(telemetry.getShutdownRetryPauseMs());
                continue;
            }
            boolean wroteSomething;
            try {
                wroteSomething = drainRetriesIgnoringSchedule() | processFreshQueue();
            } finally {
                flushInProgress.set(false);
            }
            if (!wroteSomething) {
                // Supabase is failing. Back off instead of spinning until the deadline.
                pauseBriefly(telemetry.getShutdownRetryPauseMs());
            }
        }
        reportUndrained();
    }

    int queuedEvents() {
        return freshQueue.size();
    }

    int queuedRetryBatches() {
        return retryQueue.size();
    }

    private void processDueRetries() {
        Instant now = Instant.now(clock);
        int quota = properties.getTelemetry().getRetryBatchesPerCycle();
        int inspected = 0;
        int processed = 0;
        int candidates = retryQueue.size();
        while (processed < quota && inspected < candidates) {
            TelemetryBatch batch = retryQueue.pollFirst();
            if (batch == null) {
                return;
            }
            inspected++;
            if (!batch.isDue(now)) {
                // Not due yet: keep it queued without burning the quota.
                if (!retryQueue.offerLast(batch)) {
                    abandon(batch, "retry queue full while rescheduling");
                }
                continue;
            }
            retries.increment();
            attempt(batch);
            processed++;
        }
    }

    /** @return {@code true} only if at least one batch was actually written. */
    private boolean drainRetriesIgnoringSchedule() {
        boolean wroteSomething = false;
        int candidates = retryQueue.size();
        for (int index = 0; index < candidates; index++) {
            TelemetryBatch batch = retryQueue.pollFirst();
            if (batch == null) {
                break;
            }
            retries.increment();
            wroteSomething |= attempt(batch);
        }
        return wroteSomething;
    }

    /** @return {@code true} only if at least one fresh batch was drained and written. */
    private boolean processFreshQueue() {
        SupabaseProperties.Telemetry telemetry = properties.getTelemetry();
        boolean wroteSomething = false;
        for (int cycle = 0; cycle < telemetry.getFreshBatchesPerCycle(); cycle++) {
            List<PendingRequestEvent> events = new ArrayList<>(telemetry.getBatchSize());
            freshQueue.drainTo(events, telemetry.getBatchSize());
            if (events.isEmpty()) {
                break;
            }
            wroteSomething |= attempt(TelemetryBatch.fresh(events, Instant.now(clock)));
        }
        return wroteSomething;
    }

    private boolean attempt(TelemetryBatch batch) {
        long startedNanos = System.nanoTime();
        try {
            batchWriter.write(batch);
            flushSuccess.increment();
            batchSize.record(batch.events().size());
            return true;
        } catch (RuntimeException ex) {
            flushFailure.increment();
            handleFailure(batch, ex);
            return false;
        } finally {
            flushLatency.record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void handleFailure(TelemetryBatch batch, RuntimeException cause) {
        SupabaseProperties.Telemetry telemetry = properties.getTelemetry();
        TelemetryBatch attempted = batch.withAttempt();
        if (attempted.attempts() < telemetry.getMaxAttempts()) {
            reschedule(attempted, cause);
            return;
        }
        if (attempted.isSplittable(telemetry.getMaxSplitDepth())) {
            // Isolate the poison half so the good half can still be written.
            Instant retryAt = Instant.now(clock).plusMillis(telemetry.getRetryBackoffMs());
            log.warn("Bisecting telemetry batch {} after {} failed attempts ({} events, depth {})",
                    attempted.batchId(), attempted.attempts(), attempted.events().size(),
                    attempted.splitDepth());
            for (TelemetryBatch child : attempted.bisect(retryAt)) {
                if (!retryQueue.offerLast(child)) {
                    abandon(child, "retry queue full while bisecting");
                }
            }
            return;
        }
        if (attempted.events().size() == 1) {
            poison.increment();
        }
        abandon(attempted, safeReason(cause));
    }

    private void reschedule(TelemetryBatch batch, RuntimeException cause) {
        SupabaseProperties.Telemetry telemetry = properties.getTelemetry();
        long backoff = Math.min(
                telemetry.getRetryBackoffMs() * (1L << Math.min(batch.attempts() - 1, 20)),
                telemetry.getMaxRetryBackoffMs());
        TelemetryBatch rescheduled = batch.scheduledRetry(
                Instant.now(clock).plus(Duration.ofMillis(backoff)));
        if (retryQueue.offerLast(rescheduled)) {
            log.warn("Could not write telemetry batch {} ({} events) after {} attempts; "
                            + "next attempt in {}ms: {}",
                    rescheduled.batchId(), rescheduled.events().size(), rescheduled.attempts(),
                    backoff, safeReason(cause));
            return;
        }
        abandon(rescheduled, "retry queue full");
    }

    private void abandon(TelemetryBatch batch, String reason) {
        abandoned.increment();
        dropped.increment(batch.events().size());
        int securityEvents = batch.securityEventCount();
        if (securityEvents > 0) {
            securityDropped.increment(securityEvents);
        }
        log.error("Abandoning telemetry batch {} with {} events ({} security events) after {} "
                        + "attempts at split depth {}: {}. Event ids: {}",
                batch.batchId(), batch.events().size(), securityEvents, batch.attempts(),
                batch.splitDepth(), reason, batch.requestEventIds());
    }

    private void reportUndrained() {
        int remainingEvents = freshQueue.size();
        int remainingBatches = retryQueue.size();
        if (remainingEvents == 0 && remainingBatches == 0) {
            return;
        }
        List<TelemetryBatch> stranded = new ArrayList<>();
        retryQueue.drainTo(stranded);
        int strandedEvents = stranded.stream().mapToInt(batch -> batch.events().size()).sum();
        int strandedSecurityEvents = stranded.stream().mapToInt(TelemetryBatch::securityEventCount).sum();
        List<PendingRequestEvent> strandedFresh = new ArrayList<>();
        freshQueue.drainTo(strandedFresh);
        int strandedFreshSecurityEvents =
                (int) strandedFresh.stream().filter(event -> event.securityEvent() != null).count();

        abandoned.increment(stranded.size());
        dropped.increment(strandedEvents + (double) strandedFresh.size());
        int lostSecurityEvents = strandedSecurityEvents + strandedFreshSecurityEvents;
        if (lostSecurityEvents > 0) {
            securityDropped.increment(lostSecurityEvents);
        }
        log.error("Shutdown drain deadline reached; {} queued events and {} retry batches "
                        + "({} events, {} security events) were not written",
                strandedFresh.size(), stranded.size(), strandedEvents, lostSecurityEvents);
    }

    private void offer(PendingRequestEvent event) {
        if (!acceptingEvents.get()) {
            countDrop(event);
            return;
        }
        if (freshQueue.offer(event)) {
            return;
        }
        // Queue full (B-L8): shed the OLDEST NON-SECURITY event first. Security events feed the
        // admin security monitor, and their silent loss is exactly what the H17 alerting fights;
        // regular telemetry is the cheaper casualty. Trade-off, measured by the two drop
        // counters: under sustained pressure non-security events are dropped more often.
        // Only when the queue holds nothing but security events do we fall back to evicting
        // the oldest of those.
        PendingRequestEvent evicted = null;
        java.util.Iterator<PendingRequestEvent> iterator = freshQueue.iterator();
        while (iterator.hasNext()) {
            PendingRequestEvent candidate = iterator.next();
            if (candidate.securityEvent() == null) {
                evicted = candidate;
                iterator.remove();
                break;
            }
        }
        if (evicted == null) {
            evicted = freshQueue.poll();
        }
        if (evicted != null) {
            countDrop(evicted);
        }
        if (!freshQueue.offer(event)) {
            countDrop(event);
        }
    }

    /** Test seam: whether at least one queued event carries a security event. */
    boolean hasQueuedSecurityEvent() {
        for (PendingRequestEvent pending : freshQueue) {
            if (pending.securityEvent() != null) {
                return true;
            }
        }
        return false;
    }

    private void countDrop(PendingRequestEvent event) {
        dropped.increment();
        if (event.securityEvent() != null) {
            securityDropped.increment();
        }
    }

    private void pauseBriefly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Exception type and message only: never a route, payload or credential. */
    private String safeReason(RuntimeException cause) {
        return cause.getClass().getSimpleName();
    }

    private SecurityEvent rateLimitEvent(UUID userId, String apiKeyRef) {
        return SecurityEvent.builder()
                .id(UUID.randomUUID())
                .eventType("RATE_LIMIT")
                .severity("WARNING")
                .subjectUserId(userId)
                .apiKeyRef(apiKeyRef)
                .source("HTTP")
                .description("Request rate limit exceeded")
                .build();
    }
}
