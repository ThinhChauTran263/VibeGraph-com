package com.vibegraph.infrastructure.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import com.vibegraph.infrastructure.config.InfrastructureMonitorProperties;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.OperationEvidence;
import com.vibegraph.infrastructure.persistence.InfrastructureOperationHistory;
import com.vibegraph.infrastructure.persistence.InfrastructureOperationHistoryRepository;
import com.vibegraph.infrastructure.persistence.OperationTelemetrySanitizer;

/** Bounded, best-effort operation evidence recorder; no source or credential data is retained. */
@Service
public class OperationTelemetryRecorderImpl implements OperationTelemetryRecorder {

    private static final Logger log = LoggerFactory.getLogger(OperationTelemetryRecorderImpl.class);
    private static final int MAX_ACTIVE_OPERATIONS = 32;
    private static final int MAX_PENDING_COOLDOWN_SAMPLES = 64;
    private static final int MAX_PENDING_PERSISTENCE_TASKS = 128;

    private final InfrastructureMonitorProperties properties;
    private final InfrastructureMetricsService metricsService;
    private final Clock clock;
    private final Map<String, ActiveOperation> active = new ConcurrentHashMap<>();
    private final Object activeLock = new Object();
    private final Deque<OperationEvidence> history = new ArrayDeque<>();
    private final ScheduledThreadPoolExecutor cooldownExecutor = cooldownExecutor();
    private final ThreadPoolExecutor persistenceExecutor = persistenceExecutor();
    private final Semaphore cooldownSlots = new Semaphore(MAX_PENDING_COOLDOWN_SAMPLES);
    private final ObjectProvider<InfrastructureOperationHistoryRepository> historyRepositoryProvider;

    public OperationTelemetryRecorderImpl(InfrastructureMonitorProperties properties,
            InfrastructureMetricsService metricsService, Clock clock) {
        this(properties, metricsService, clock, null);
    }

    @Autowired
    public OperationTelemetryRecorderImpl(InfrastructureMonitorProperties properties,
            InfrastructureMetricsService metricsService, Clock clock,
            ObjectProvider<InfrastructureOperationHistoryRepository> historyRepositoryProvider) {
        this.properties = properties;
        this.metricsService = metricsService;
        this.clock = clock;
        this.historyRepositoryProvider = historyRepositoryProvider;
        loadPersistedHistory();
    }

    private ScheduledThreadPoolExecutor cooldownExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "infrastructure-cooldown");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    @Override
    public OperationToken begin(String type, String operation, String projectId, String projectName) {
        String id = eventId();
        try {
            synchronized (activeLock) {
                if (active.size() >= MAX_ACTIVE_OPERATIONS) {
                    log.warn("Infrastructure telemetry active-operation limit reached; operation {} will run untracked",
                            safe(operation, 80));
                    return new OperationToken(null);
                }
                InfrastructureSnapshot snapshot = metricsService.snapshot();
                String normalizedType = safe(type, 32);
                ActiveOperation item = new ActiveOperation(
                        id, UUID.randomUUID().toString(), safe(projectId, 64), safe(projectName, 120),
                        normalizedType == null ? "OTHER" : normalizedType.toUpperCase(), safe(operation, 80),
                        Instant.now(clock), snapshot);
                active.put(id, item);
                item.observeConcurrency(active.size());
                return new OperationToken(id, false);
            }
        } catch (RuntimeException ex) {
            log.warn("Infrastructure telemetry snapshot unavailable; operation {} will run untracked",
                    safe(operation, 80));
            return new OperationToken(null);
        }
    }

    @Override
    public void attach(OperationToken token, String projectId, String projectName) {
        if (token == null || token.terminal() || token.id() == null) return;
        ActiveOperation item = active.get(token.id());
        if (item != null) item.attach(safe(projectId, 64), safe(projectName, 120));
    }

    /** Updates peak values for all active operations from the one-second host sample. */
    public void observe(InfrastructureSnapshot snapshot) {
        int concurrent = active.size();
        active.values().forEach(item -> {
            item.observe(snapshot);
            item.observeConcurrency(concurrent);
        });
    }

    @Override
    public void complete(OperationToken token, int nodes, int edges, long storageAddedBytes) {
        finish(token, "SUCCESS", nodes, edges, storageAddedBytes, null);
    }

    @Override
    public void fail(OperationToken token, Throwable error) {
        finish(token, "FAILED", 0, 0, 0, "ERROR");
    }

    @Override
    public void stop(OperationToken token, String reason) {
        finish(token, "STOPPED", 0, 0, 0, safe(reason, 80));
    }

    @Override
    public List<OperationEvidence> recent(int limit, String type) {
        int bounded = Math.min(Math.max(limit, 1), properties.getOperationHistoryCapacity());
        String filter = type == null ? "ALL" : type.trim().toUpperCase();
        synchronized (history) {
            return history.stream()
                    .filter(item -> "ALL".equals(filter) || filter.equals(item.type()))
                    .limit(bounded)
                    .map(OperationTelemetrySanitizer::evidence)
                    .toList();
        }
    }

    private void finish(OperationToken token, String status, int nodes, int edges,
            long storageAddedBytes, String stopReason) {
        if (token == null || token.id() == null || token.terminal()) return;
        ActiveOperation item;
        int concurrentAtFinish;
        synchronized (activeLock) {
            item = active.remove(token.id());
            concurrentAtFinish = active.size() + (item == null ? 0 : 1);
        }
        if (item == null) return;
        InfrastructureSnapshot after;
        try {
            after = metricsService.snapshot();
        } catch (RuntimeException ex) {
            after = item.before;
        }
        item.observe(after);
        Instant completedAt = Instant.now(clock);
        long duration = Math.max(0, Duration.between(item.startedAt, completedAt).toMillis());
        item.observeConcurrency(concurrentAtFinish);
        item.finalizeCpu(duration / 1_000d, after.host().cpuPercent(), after.host().vcpuCount(), completedAt);
        item.finalizeDisk(duration / 1_000d, diskReadRate(after), diskWriteRate(after), completedAt);
        long increase = Math.max(0, item.ramPeak - item.before.memory().usedBytes());
        double cpuAverage = item.averageCpuPercent(after.host().cpuPercent());
        double coreSeconds = item.cpuCoreSeconds;
        boolean cooldownComplete = properties.getOperationCooldownMs() <= 0;
        OperationEvidence evidence = new OperationEvidence(
                item.id, item.traceId, item.projectId, item.projectName, item.type, item.operation, status,
                item.startedAt, completedAt, duration, Math.max(0, nodes), Math.max(0, edges),
                item.before.memory().usedBytes(), item.ramPeak, increase, after.memory().usedBytes(),
                cooldownComplete,
                cpuAverage,
                item.cpuPeak, coreSeconds, Math.max(0, storageAddedBytes),
                item.diskReadBytes, item.diskWriteBytes, item.peakConcurrentOperations,
                "runtime", "OBSERVED", item.cpuSamples >= 3 ? "HIGH" : "LOW", stopReason);
        appendHistory(evidence);
        scheduleCooldownSample(evidence);
    }

    private String eventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Records a bounded-admission refusal instead of returning a token that can disappear. */
    private void recordAdmissionStop(String id, String type, String operation, String projectId,
            String projectName, String reason) {
        Instant now = Instant.now(clock);
        OperationEvidence evidence = new OperationEvidence(
                id, UUID.randomUUID().toString(), safe(projectId, 64), safe(projectName, 120),
                normalizeType(type), safe(operation, 80), "STOPPED", now, now, 0, 0, 0,
                0, 0, 0, 0, true, 0, 0, 0, 0, 0, 0, 0,
                "runtime", "ESTIMATED", "LOW", reason);
        appendHistory(evidence);
    }

    private void appendHistory(OperationEvidence evidence) {
        OperationEvidence safeEvidence = OperationTelemetrySanitizer.evidence(evidence);
        if (safeEvidence == null || safeEvidence.id() == null) return;
        synchronized (history) {
            history.addFirst(safeEvidence);
            while (history.size() > properties.getOperationHistoryCapacity()) history.removeLast();
        }
        InfrastructureOperationHistoryRepository repository = historyRepository();
        if (repository == null) return;
        submitPersistence(() -> {
            try {
                repository.save(InfrastructureOperationHistory.from(safeEvidence));
                retainPersistedHistory(repository);
            } catch (RuntimeException ex) {
                log.warn("Unable to persist infrastructure operation evidence {}: {}",
                        safeEvidence.id(), ex.getMessage());
            }
        });
    }

    /** Takes a delayed host sample without blocking the operation's response path. */
    private void scheduleCooldownSample(OperationEvidence evidence) {
        long delay = properties.getOperationCooldownMs();
        if (delay <= 0 || !cooldownSlots.tryAcquire()) return;
        try {
            cooldownExecutor.schedule(() -> {
                try {
                    InfrastructureSnapshot after = metricsService.snapshot();
                    OperationEvidence cooled = new OperationEvidence(
                            evidence.id(), evidence.traceId(), evidence.projectId(), evidence.projectName(),
                            evidence.type(), evidence.operation(), evidence.status(), evidence.startedAt(),
                            evidence.completedAt(), evidence.durationMs(), evidence.nodes(), evidence.edges(),
                            evidence.ramBeforeBytes(), evidence.ramPeakBytes(), evidence.ramIncreaseBytes(),
                            after.memory().usedBytes(), true, evidence.cpuAvgPercent(), evidence.cpuPeakPercent(),
                            evidence.cpuCoreSeconds(), evidence.storageAddedBytes(), evidence.diskReadBytes(),
                            evidence.diskWriteBytes(), evidence.concurrentHeavyOperations(), evidence.backendVersion(),
                            evidence.measurementType(), evidence.confidence(), evidence.stopReason());
                    replaceHistory(cooled);
                } catch (RuntimeException ignored) {
                    // Monitoring is best-effort and must never affect the operation path.
                } finally {
                    cooldownSlots.release();
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RuntimeException ex) {
            cooldownSlots.release();
        }
    }

    private void replaceHistory(OperationEvidence replacement) {
        OperationEvidence safeReplacement = OperationTelemetrySanitizer.evidence(replacement);
        if (safeReplacement == null || safeReplacement.id() == null) return;
        boolean present;
        synchronized (history) {
            present = history.removeIf(item -> item.id().equals(safeReplacement.id()));
            if (present) history.addFirst(safeReplacement);
        }
        if (!present) return;
        InfrastructureOperationHistoryRepository repository = historyRepository();
        if (repository == null) return;
        submitPersistence(() -> {
            try {
                repository.findById(safeReplacement.id()).ifPresentOrElse(row -> {
                    row.applyCooldown(safeReplacement.ramAfterCooldownBytes());
                    repository.save(row);
                }, () -> repository.save(InfrastructureOperationHistory.from(safeReplacement)));
            } catch (RuntimeException ex) {
                log.warn("Unable to persist cooldown evidence {}: {}",
                        safeReplacement.id(), ex.getMessage());
            }
        });
    }

    private InfrastructureOperationHistoryRepository historyRepository() {
        return historyRepositoryProvider == null
                ? null
                : historyRepositoryProvider.getIfAvailable(() -> null);
    }

    private void loadPersistedHistory() {
        InfrastructureOperationHistoryRepository repository = historyRepository();
        if (repository == null) return;
        try {
            List<InfrastructureOperationHistory> rows = repository.findAllByOrderByCompletedAtDescIdDesc(
                    PageRequest.of(0, properties.getOperationHistoryCapacity()));
            synchronized (history) {
                // The repository returns newest-first; ArrayDeque preserves that order when rows
                // are appended at the tail, so recent() remains newest-first after a restart.
                rows.stream().map(InfrastructureOperationHistory::toEvidence).forEach(history::addLast);
            }
            retainPersistedHistory(repository);
        } catch (RuntimeException ex) {
            log.warn("Unable to load infrastructure operation history: {}", ex.getMessage());
        }
    }

    private void retainPersistedHistory(InfrastructureOperationHistoryRepository repository) {
        int capacity = properties.getOperationHistoryCapacity();
        if (capacity < 1) return;
        List<InfrastructureOperationHistory> newest = repository.findAllByOrderByCompletedAtDescIdDesc(
                PageRequest.of(0, capacity));
        Collection<String> retainedIds = newest.stream()
                .map(InfrastructureOperationHistory::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (!retainedIds.isEmpty()) {
            repository.deleteByIdNotIn(retainedIds);
        }
    }

    @PreDestroy
    void shutdownCooldownExecutor() {
        cooldownExecutor.shutdownNow();
        persistenceExecutor.shutdownNow();
    }

    private String safe(String value, int max) {
        return OperationTelemetrySanitizer.text(value, max);
    }

    private ThreadPoolExecutor persistenceExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_PERSISTENCE_TASKS), runnable -> {
                    Thread thread = new Thread(runnable, "infrastructure-history-persistence");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    private void submitPersistence(Runnable task) {
        if (persistenceExecutor.isShutdown()) return;
        try {
            persistenceExecutor.execute(task);
        } catch (RejectedExecutionException ignored) {
            // Shutdown races and queue pressure must never affect the user operation.
        }
    }

    private String normalizeType(String value) {
        return OperationTelemetrySanitizer.type(value);
    }

    private static final class ActiveOperation {
        private final String id;
        private final String traceId;
        private volatile String projectId;
        private volatile String projectName;
        private final String type;
        private final String operation;
        private final Instant startedAt;
        private final InfrastructureSnapshot before;
        private Instant lastObservedAt;
        private double lastCpuPercent;
        private int lastVcpuCount;
        private double integratedCpuPercentSeconds;
        private double cpuCoreSeconds;
        private double integratedSeconds;
        private Instant lastDiskObservedAt;
        private long lastDiskReadRate;
        private long lastDiskWriteRate;
        private double integratedDiskSeconds;
        private long diskReadBytes;
        private long diskWriteBytes;
        private long ramPeak;
        private double cpuPeak;
        private long cpuSamples;
        private int peakConcurrentOperations = 1;

        private ActiveOperation(String id, String traceId, String projectId, String projectName,
                String type, String operation, Instant startedAt, InfrastructureSnapshot before) {
            this.id = id;
            this.traceId = traceId;
            this.projectId = projectId;
            this.projectName = projectName;
            this.type = type;
            this.operation = operation;
            this.startedAt = startedAt;
            this.before = before;
            this.ramPeak = before.memory().usedBytes();
            this.lastObservedAt = startedAt;
            this.lastCpuPercent = boundedPercent(before.host().cpuPercent());
            this.lastVcpuCount = Math.max(1, before.host().vcpuCount());
            this.cpuPeak = lastCpuPercent;
            this.lastDiskObservedAt = startedAt;
            this.lastDiskReadRate = diskReadRate(before);
            this.lastDiskWriteRate = diskWriteRate(before);
        }

        private synchronized void observe(InfrastructureSnapshot snapshot) {
            if (snapshot == null) return;
            ramPeak = Math.max(ramPeak, snapshot.memory().usedBytes());
            Instant observedAt = snapshot.capturedAt() == null ? lastObservedAt : snapshot.capturedAt();
            double currentCpu = boundedPercent(snapshot.host().cpuPercent());
            int currentVcpu = Math.max(1, snapshot.host().vcpuCount());
            integrateDisk(observedAt, diskReadRate(snapshot), diskWriteRate(snapshot));
            integrate(observedAt, currentCpu, currentVcpu);
            cpuSamples++;
        }

        private synchronized void observeConcurrency(int concurrentOperations) {
            peakConcurrentOperations = Math.max(peakConcurrentOperations, concurrentOperations);
        }

        private synchronized void attach(String projectId, String projectName) {
            if (projectId != null) this.projectId = projectId;
            if (projectName != null) this.projectName = projectName;
        }

        private synchronized void finalizeCpu(double durationSeconds, double finalCpu, int finalVcpu,
                Instant completedAt) {
            double safeDuration = Math.max(0d, durationSeconds);
            integrate(completedAt, boundedPercent(finalCpu), Math.max(1, finalVcpu));
            double remaining = safeDuration - integratedSeconds;
            if (remaining > 0d) {
                double cpu = boundedPercent(finalCpu);
                integratedCpuPercentSeconds += cpu * remaining;
                cpuCoreSeconds += (cpu / 100d) * Math.max(1, finalVcpu) * remaining;
                integratedSeconds += remaining;
                cpuPeak = Math.max(cpuPeak, cpu);
            }
        }

        private synchronized void finalizeDisk(double durationSeconds, long finalReadRate,
                long finalWriteRate, Instant completedAt) {
            integrateDisk(completedAt, finalReadRate, finalWriteRate);
            double remaining = Math.max(0d, durationSeconds - integratedDiskSeconds);
            if (remaining > 0d) {
                diskReadBytes = saturatedAdd(diskReadBytes, Math.round(finalReadRate * remaining));
                diskWriteBytes = saturatedAdd(diskWriteBytes, Math.round(finalWriteRate * remaining));
            }
        }

        private void integrateDisk(Instant observedAt, long readRate, long writeRate) {
            if (observedAt == null || lastDiskObservedAt == null || observedAt.isBefore(lastDiskObservedAt)) return;
            double seconds = Duration.between(lastDiskObservedAt, observedAt).toNanos() / 1_000_000_000d;
            if (seconds > 0d) {
                diskReadBytes = saturatedAdd(diskReadBytes, Math.round(lastDiskReadRate * seconds));
                diskWriteBytes = saturatedAdd(diskWriteBytes, Math.round(lastDiskWriteRate * seconds));
                integratedDiskSeconds += seconds;
            }
            lastDiskReadRate = Math.max(0L, readRate);
            lastDiskWriteRate = Math.max(0L, writeRate);
            lastDiskObservedAt = observedAt;
        }

        private synchronized double averageCpuPercent(double fallback) {
            return integratedSeconds > 0d
                    ? boundedPercent(integratedCpuPercentSeconds / integratedSeconds)
                    : boundedPercent(fallback);
        }

        private void integrate(Instant observedAt, double currentCpu, int currentVcpu) {
            if (observedAt == null || lastObservedAt == null || observedAt.isBefore(lastObservedAt)) return;
            double seconds = Duration.between(lastObservedAt, observedAt).toNanos() / 1_000_000_000d;
            if (seconds <= 0d) return;
            double averageCpu = (lastCpuPercent + currentCpu) / 2d;
            integratedCpuPercentSeconds += averageCpu * seconds;
            cpuCoreSeconds += (averageCpu / 100d) * lastVcpuCount * seconds;
            integratedSeconds += seconds;
            cpuPeak = Math.max(cpuPeak, currentCpu);
            lastCpuPercent = currentCpu;
            lastVcpuCount = currentVcpu;
            lastObservedAt = observedAt;
        }

        private static double boundedPercent(double value) {
            return Double.isFinite(value) ? Math.min(100d, Math.max(0d, value)) : 0d;
        }

        private static long diskReadRate(InfrastructureSnapshot snapshot) {
            return OperationTelemetryRecorderImpl.diskReadRate(snapshot);
        }

        private static long diskWriteRate(InfrastructureSnapshot snapshot) {
            return OperationTelemetryRecorderImpl.diskWriteRate(snapshot);
        }
    }

    private static long diskReadRate(InfrastructureSnapshot snapshot) {
        return snapshot == null || snapshot.diskIo() == null
                ? 0L : Math.max(0L, snapshot.diskIo().readBytesPerSecond());
    }

    private static long diskWriteRate(InfrastructureSnapshot snapshot) {
        return snapshot == null || snapshot.diskIo() == null
                ? 0L : Math.max(0L, snapshot.diskIo().writeBytesPerSecond());
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0) return left;
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
