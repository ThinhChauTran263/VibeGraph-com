package com.vibegraph.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;

import com.vibegraph.infrastructure.config.InfrastructureMonitorProperties;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot;
import com.vibegraph.infrastructure.persistence.entity.InfrastructureOperationHistory;
import com.vibegraph.infrastructure.persistence.InfrastructureOperationHistoryRepository;

class OperationTelemetryRecorderImplTest {

    @Test
    void complete_integratesObservedHostDiskRates() {
        InfrastructureMetricsService metrics = mock(InfrastructureMetricsService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
        when(metrics.snapshot()).thenReturn(snapshot(clock.instant(), 1_000, 500),
                snapshot(clock.instant().plusSeconds(10), 2_000, 1_000));
        InfrastructureMonitorProperties properties = properties(10);
        properties.setOperationCooldownMs(0);
        OperationTelemetryRecorderImpl recorder = new OperationTelemetryRecorderImpl(properties, metrics, clock);
        try {
            var token = recorder.begin("API", "graph", "p1", "Demo");
            clock.advanceSeconds(10);
            recorder.complete(token, 5, 7, 0);

            var evidence = recorder.recent(1, "API").getFirst();
            assertThat(evidence.diskReadBytes()).isEqualTo(10_000);
            assertThat(evidence.diskWriteBytes()).isEqualTo(5_000);
        } finally {
            recorder.shutdownCooldownExecutor();
        }
    }

    @Test
    void attach_updatesProjectIdentityBeforeEvidenceIsCompleted() {
        InfrastructureMetricsService metrics = mock(InfrastructureMetricsService.class);
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        when(metrics.snapshot()).thenReturn(snapshot(now, 0, 0));
        InfrastructureMonitorProperties properties = properties(10);
        properties.setOperationCooldownMs(0);
        OperationTelemetryRecorderImpl recorder = new OperationTelemetryRecorderImpl(
                properties, metrics, Clock.fixed(now, ZoneOffset.UTC));
        try {
            var token = recorder.begin("IMPORT", "archive-import", null, "Requested name");
            recorder.attach(token, "project-42", "Registered project");
            recorder.complete(token, 5, 7, 12);

            var evidence = recorder.recent(1, "IMPORT").getFirst();
            assertThat(evidence.projectId()).isEqualTo("project-42");
            assertThat(evidence.projectName()).isEqualTo("Registered project");
        } finally {
            recorder.shutdownCooldownExecutor();
        }
    }

    @Test
    void persistedHistory_loadsNewestFirstAndRetentionDeletesOlderRows() {
        InfrastructureOperationHistoryRepository repository = mock(InfrastructureOperationHistoryRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<InfrastructureOperationHistoryRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(repository);
        InfrastructureOperationHistory newest = row("newest", Instant.parse("2026-08-25T00:00:02Z"));
        InfrastructureOperationHistory older = row("older", Instant.parse("2026-08-25T00:00:01Z"));
        when(repository.findAllByOrderByCompletedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(List.of(newest, older), List.of(newest, older));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        InfrastructureMetricsService metrics = mock(InfrastructureMetricsService.class);
        when(metrics.snapshot()).thenReturn(snapshot(Instant.parse("2026-08-25T00:00:03Z"), 0, 0));
        OperationTelemetryRecorderImpl recorder = new OperationTelemetryRecorderImpl(
                properties(2), metrics, Clock.fixed(Instant.parse("2026-08-25T00:00:03Z"), ZoneOffset.UTC), provider);
        try {
            assertThat(recorder.recent(2, "ALL")).extracting(InfrastructureSnapshot.OperationEvidence::id)
                    .containsExactly("newest", "older");

            var token = recorder.begin("API", "graph", "p1", null);
            recorder.complete(token, 1, 1, 0);

            verify(repository, timeout(1_000).atLeastOnce()).deleteByIdNotIn(anyCollection());
        } finally {
            recorder.shutdownCooldownExecutor();
        }
    }

    @Test
    void publicHistoryRedactsSensitiveLabelsBeforePersistence() {
        InfrastructureMetricsService metrics = mock(InfrastructureMetricsService.class);
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        when(metrics.snapshot()).thenReturn(snapshot(now, 0, 0));
        InfrastructureMonitorProperties properties = properties(10);
        properties.setOperationCooldownMs(0);
        OperationTelemetryRecorderImpl recorder = new OperationTelemetryRecorderImpl(
                properties, metrics, Clock.fixed(now, ZoneOffset.UTC));
        try {
            var token = recorder.begin("API", "Authorization Bearer hidden", "p1", "password=hidden");
            recorder.complete(token, 1, 1, 0);

            var evidence = recorder.recent(1, "API").getFirst();
            assertThat(evidence.operation()).isNull();
            assertThat(evidence.projectName()).isNull();
        } finally {
            recorder.shutdownCooldownExecutor();
        }
    }

    @Test
    void persistenceCannotBlockOperationCompletion() throws Exception {
        InfrastructureOperationHistoryRepository repository = mock(InfrastructureOperationHistoryRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<InfrastructureOperationHistoryRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(repository);
        when(repository.findAllByOrderByCompletedAtDescIdDesc(any(Pageable.class))).thenReturn(List.of());
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch releasePersistence = new CountDownLatch(1);
        when(repository.save(any())).thenAnswer(invocation -> {
            persistenceStarted.countDown();
            releasePersistence.await(2, TimeUnit.SECONDS);
            return invocation.getArgument(0);
        });
        InfrastructureMetricsService metrics = mock(InfrastructureMetricsService.class);
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        when(metrics.snapshot()).thenReturn(snapshot(now, 0, 0));
        InfrastructureMonitorProperties properties = properties(10);
        properties.setOperationCooldownMs(0);
        OperationTelemetryRecorderImpl recorder = new OperationTelemetryRecorderImpl(
                properties, metrics, Clock.fixed(now, ZoneOffset.UTC), provider);
        try {
            var token = recorder.begin("API", "graph", "p1", "Demo");
            long started = System.nanoTime();
            recorder.complete(token, 1, 1, 0);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(elapsedMs).isLessThan(200);
            assertThat(persistenceStarted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            releasePersistence.countDown();
            recorder.shutdownCooldownExecutor();
        }
    }

    @Test
    void unavailableMonitoringDoesNotRejectTheUserOperation() {
        InfrastructureMetricsService metrics = mock(InfrastructureMetricsService.class);
        when(metrics.snapshot()).thenThrow(new IllegalStateException("monitor unavailable"));
        OperationTelemetryRecorderImpl recorder = new OperationTelemetryRecorderImpl(
                properties(10), metrics, Clock.systemUTC());
        try {
            var token = recorder.begin("API", "graph", "p1", "Demo");

            assertThat(token.terminal()).isFalse();
            assertThatCode(() -> OperationTelemetryRecorder.requireAccepted(token))
                    .doesNotThrowAnyException();
        } finally {
            recorder.shutdownCooldownExecutor();
        }
    }

    private InfrastructureMonitorProperties properties(int capacity) {
        InfrastructureMonitorProperties properties = new InfrastructureMonitorProperties();
        properties.setOperationHistoryCapacity(capacity);
        properties.setOperationHistoryInSnapshot(Math.min(capacity, 2));
        return properties;
    }

    private InfrastructureOperationHistory row(String id, Instant completedAt) {
        return InfrastructureOperationHistory.from(new InfrastructureSnapshot.OperationEvidence(
                id, "trace-" + id, "p1", "Demo", "API", "graph", "SUCCESS",
                completedAt.minusSeconds(1), completedAt, 1_000, 1, 1,
                100, 200, 100, 100, true, 10, 20, 0.2,
                0, 0, 0, 1, "runtime", "OBSERVED", "HIGH", null));
    }

    private InfrastructureSnapshot snapshot(Instant capturedAt, long readRate, long writeRate) {
        return new InfrastructureSnapshot(capturedAt, "HEALTHY",
                new InfrastructureSnapshot.HostMetrics(10, 4, 2.5, 10, 10, "test", "MEASURED"),
                new InfrastructureSnapshot.MemoryMetrics(1_000, 500, 500, 50, List.of(), "test", "MEASURED"),
                new InfrastructureSnapshot.DiskMetrics(1_000, 500, 500, 50, List.of(), "test", "MEASURED"),
                new InfrastructureSnapshot.NetworkMetrics(0, 0, 0, "test", "MEASURED"),
                new InfrastructureSnapshot.DiskIoMetrics(readRate, writeRate, 1d, "test", "MEASURED"),
                List.of(), null, null, List.of(), List.of());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
