package com.vibegraph.abuse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A unit of telemetry work with a stable identity.
 *
 * <p>Retrying a batch reuses the same {@code batchId} and the same event ids, so a write that
 * partially succeeded before failing is replayed idempotently instead of producing duplicates.
 * Individual events are never returned to the fresh queue; the batch travels as a whole.
 *
 * <p>A batch that keeps failing is bisected. Child ids are derived from the parent id and the
 * split path, so isolating a poison event produces the same identity on every attempt.
 *
 * <p>Batches live in memory only. A process crash loses whatever has not been written yet;
 * request and security telemetry is best-effort, not durable.
 *
 * @param events     request events paired with the security event they belong to, if any
 * @param attempts   how many write attempts this batch identity has already had
 * @param nextRetryAt earliest instant at which this batch may be retried
 * @param splitDepth how many times an ancestor of this batch was bisected
 */
public record TelemetryBatch(
        String batchId,
        List<PendingRequestEvent> events,
        int attempts,
        Instant nextRetryAt,
        Instant createdAt,
        int splitDepth) {

    public TelemetryBatch {
        events = List.copyOf(events);
    }

    /** Creates a new batch identity for freshly drained events. */
    public static TelemetryBatch fresh(List<PendingRequestEvent> events, Instant now) {
        return new TelemetryBatch(UUID.randomUUID().toString(), events, 0, now, now, 0);
    }

    /** Same identity, payload and attempt count, scheduled for its next attempt. */
    public TelemetryBatch scheduledRetry(Instant retryAt) {
        return new TelemetryBatch(batchId, events, attempts, retryAt, createdAt, splitDepth);
    }

    /** Records that one more write attempt has been made. */
    public TelemetryBatch withAttempt() {
        return new TelemetryBatch(batchId, events, attempts + 1, nextRetryAt, createdAt, splitDepth);
    }

    public boolean isDue(Instant now) {
        return !nextRetryAt.isAfter(now);
    }

    public boolean isSplittable(int maxSplitDepth) {
        return events.size() > 1 && splitDepth < maxSplitDepth;
    }

    /**
     * Splits this batch in half. The two children keep the parent id plus a deterministic suffix,
     * so the same bisect performed twice yields the same child identities.
     */
    public List<TelemetryBatch> bisect(Instant retryAt) {
        int middle = events.size() / 2;
        return List.of(
                child("L", events.subList(0, middle), retryAt),
                child("R", events.subList(middle, events.size()), retryAt));
    }

    /** Request event ids only. Safe to log: no route, payload or credential material. */
    public List<UUID> requestEventIds() {
        return events.stream().map(event -> event.requestEvent().getId()).toList();
    }

    public int securityEventCount() {
        return (int) events.stream().filter(event -> event.securityEvent() != null).count();
    }

    private TelemetryBatch child(String branch, List<PendingRequestEvent> childEvents, Instant retryAt) {
        return new TelemetryBatch(
                batchId + "/" + branch, childEvents, 0, retryAt, createdAt, splitDepth + 1);
    }
}
