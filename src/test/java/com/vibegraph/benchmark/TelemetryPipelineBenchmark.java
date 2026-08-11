package com.vibegraph.benchmark;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.abuse.RequestEventBatchWriter;
import com.vibegraph.abuse.RequestEventService;
import com.vibegraph.abuse.TelemetryBatch;
import com.vibegraph.auth.service.AdminSecurityRequestEventPublisher;
import com.vibegraph.common.supabase.SupabaseProperties;
import com.vibegraph.common.supabase.repository.JdbcRequestEventRepository;
import com.vibegraph.common.supabase.repository.JdbcSecurityEventRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Capacity benchmark for the telemetry pipeline against a containerised PostgreSQL.
 *
 * <p>Opt-in only: the class name does not match the default Surefire includes, and it is wired to
 * the {@code benchmark} Maven profile, so {@code mvn test} never runs it. Re-run it with:
 *
 * <pre>mvn -o -Pbenchmark test</pre>
 *
 * <p>It writes {@code target/benchmarks/telemetry-pipeline.json} alongside a structured console
 * summary. The numbers describe the machine that produced them; a laptop with a container-local
 * database is not a stand-in for a managed Supabase instance across a network, so nothing here may
 * be copied into production configuration without a rehearsal on representative infrastructure.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Telemetry pipeline capacity benchmark")
class TelemetryPipelineBenchmark {

    private static final String SCHEMA = "vibegraph_realtime";
    private static final int PRODUCER_THREADS = 4;
    private static final int EVENTS_PER_PRODUCER = 12_500;
    private static final long FLUSH_INTERVAL_MS = 200;
    private static final int FRESH_BATCHES_PER_CYCLE = 40;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    @DisplayName("measure arrival rate, drain rate, queue utilization and storage per event")
    void measurePipelineCapacity() throws Exception {
        try (HikariDataSource dataSource = pool()) {
            org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(SCHEMA)
                    .defaultSchema(SCHEMA)
                    .locations("classpath:db/supabase")
                    .load()
                    .migrate();

            NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
            jdbc.getJdbcTemplate().update("DELETE FROM request_events");
            jdbc.getJdbcTemplate().update("DELETE FROM security_events");

            MeterRegistry registry = new SimpleMeterRegistry();
            SupabaseProperties properties = new SupabaseProperties();
            properties.getTelemetry().setFlushIntervalMs(FLUSH_INTERVAL_MS);
            // Raised above the default of 1 so the measurement finds the database write ceiling
            // rather than the batchSize / flushInterval cap.
            properties.getTelemetry().setFreshBatchesPerCycle(FRESH_BATCHES_PER_CYCLE);
            RequestEventBatchWriter writer = transactionalWriter(dataSource, jdbc);
            RequestEventService service = new RequestEventService(
                    writer, properties, registry, Clock.systemUTC());

            // Phase 1: unpaced producers, to find the drain rate the pipeline can actually sustain
            // and to observe what overflow does.
            BenchmarkResult saturation = run(service, registry, properties, 0);
            saturation.storedRows = jdbc.getJdbcTemplate().queryForObject(
                    "SELECT count(*) FROM request_events", Long.class);
            measureStorage(jdbc, saturation);

            // Phase 2: arrival paced at half the observed drain rate, i.e. the 2x drain safety
            // margin used for capacity planning. Drops here mean the margin is not real.
            long pacedRate = Math.max((long) (saturation.observedDrainRatePerSecond() / 2), 100);
            jdbc.getJdbcTemplate().update("DELETE FROM request_events");
            MeterRegistry pacedRegistry = new SimpleMeterRegistry();
            RequestEventService pacedService = new RequestEventService(
                    writer, properties, pacedRegistry, Clock.systemUTC());
            BenchmarkResult paced = run(pacedService, pacedRegistry, properties, pacedRate);
            paced.storedRows = jdbc.getJdbcTemplate().queryForObject(
                    "SELECT count(*) FROM request_events", Long.class);

            Map<String, Object> measurements = new LinkedHashMap<>();
            measurements.put("saturation", saturation.toMap());
            measurements.put("pacedAtHalfDrainRate", paced.toMap());
            measurements.put("targetPacedRatePerSecond", pacedRate);
            measurements.put("drainSafetyMarginHolds",
                    paced.dropped == 0 && paced.abandoned == 0);
            BenchmarkReport.write("telemetry-pipeline", measurements);
        }
    }

    /**
     * @param targetRatePerSecond total arrival rate to pace towards, or {@code 0} for unpaced
     *                            producers that saturate the pipeline
     */
    private BenchmarkResult run(RequestEventService service, MeterRegistry registry,
            SupabaseProperties properties, long targetRatePerSecond) throws Exception {
        BenchmarkResult result = new BenchmarkResult();
        result.targetRatePerSecond = targetRatePerSecond;
        result.producerThreads = PRODUCER_THREADS;
        result.eventsOffered = (long) PRODUCER_THREADS * EVENTS_PER_PRODUCER;
        result.queueCapacity = properties.getTelemetry().getQueueCapacity();
        result.batchSize = properties.getTelemetry().getBatchSize();
        result.freshBatchesPerCycle = properties.getTelemetry().getFreshBatchesPerCycle();
        result.flushIntervalMs = properties.getTelemetry().getFlushIntervalMs();

        AtomicLong peakFreshQueue = new AtomicLong();
        AtomicLong peakRetryQueue = new AtomicLong();
        ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();
        scheduled.scheduleWithFixedDelay(() -> {
            service.flush();
            peakFreshQueue.accumulateAndGet(queueGauge(registry,
                    RequestEventServiceMetrics.FRESH_QUEUE), Math::max);
            peakRetryQueue.accumulateAndGet(queueGauge(registry,
                    RequestEventServiceMetrics.RETRY_QUEUE), Math::max);
        }, 0, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);

        ExecutorService producers = Executors.newFixedThreadPool(PRODUCER_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        long arrivalStart;
        try {
            for (int thread = 0; thread < PRODUCER_THREADS; thread++) {
                final int producerId = thread;
                producers.submit(() -> {
                    start.await();
                    long intervalNanos = targetRatePerSecond <= 0
                            ? 0
                            : TimeUnit.SECONDS.toNanos(1) * PRODUCER_THREADS / targetRatePerSecond;
                    long nextAt = System.nanoTime();
                    for (int index = 0; index < EVENTS_PER_PRODUCER; index++) {
                        if (intervalNanos > 0) {
                            nextAt += intervalNanos;
                            long waitNanos = nextAt - System.nanoTime();
                            if (waitNanos > 0) {
                                TimeUnit.NANOSECONDS.sleep(waitNanos);
                            }
                        }
                        service.record(null, null, "10." + producerId + ".0.1",
                                "/api/projects/{id}", "GET", 200, Instant.now(), "REQUEST");
                    }
                    return null;
                });
            }
            arrivalStart = System.nanoTime();
            start.countDown();
            producers.shutdown();
            if (!producers.awaitTermination(10, TimeUnit.MINUTES)) {
                throw new IllegalStateException("producers did not finish in time");
            }
        }
        finally {
            if (!producers.isShutdown()) {
                producers.shutdownNow();
            }
        }
        result.arrivalMillis = Duration.ofNanos(System.nanoTime() - arrivalStart).toMillis();

        long drainStart = System.nanoTime();
        service.drainBeforeShutdown();
        result.drainMillis = Duration.ofNanos(System.nanoTime() - drainStart).toMillis();
        scheduled.shutdownNow();

        result.peakFreshQueue = peakFreshQueue.get();
        result.peakRetryQueue = peakRetryQueue.get();
        result.dropped = (long) registry.counter(RequestEventServiceMetrics.DROPPED).count();
        result.securityDropped = (long) registry.counter(RequestEventServiceMetrics.SECURITY_DROPPED).count();
        result.flushSuccess = (long) registry.counter(RequestEventServiceMetrics.FLUSH_SUCCESS).count();
        result.flushFailure = (long) registry.counter(RequestEventServiceMetrics.FLUSH_FAILURE).count();
        result.retries = (long) registry.counter(RequestEventServiceMetrics.RETRY).count();
        result.abandoned = (long) registry.counter(RequestEventServiceMetrics.ABANDONED).count();
        result.poison = (long) registry.counter(RequestEventServiceMetrics.POISON).count();
        result.concurrentSkipped = (long) registry.counter(RequestEventServiceMetrics.CONCURRENT_SKIPPED).count();
        result.meanFlushLatencyMs = registry.timer(RequestEventServiceMetrics.FLUSH_LATENCY)
                .mean(TimeUnit.MILLISECONDS);
        result.maxFlushLatencyMs = registry.timer(RequestEventServiceMetrics.FLUSH_LATENCY)
                .max(TimeUnit.MILLISECONDS);
        return result;
    }

    /**
     * Storage per stored event including indexes and TOAST. Division is guarded because an empty
     * table would otherwise report an infinite cost per row.
     */
    private void measureStorage(NamedParameterJdbcTemplate jdbc, BenchmarkResult result) {
        result.totalRelationBytes = jdbc.getJdbcTemplate().queryForObject(
                "SELECT pg_total_relation_size('" + SCHEMA + ".request_events'::regclass)", Long.class);
        result.indexBytes = jdbc.getJdbcTemplate().queryForObject(
                "SELECT pg_indexes_size('" + SCHEMA + ".request_events'::regclass)", Long.class);
        if (result.storedRows != null && result.storedRows > 0 && result.totalRelationBytes != null) {
            result.bytesPerEvent = (double) result.totalRelationBytes / result.storedRows;
        }
    }

    private long queueGauge(MeterRegistry registry, String name) {
        var gauge = registry.find(name).gauge();
        if (gauge == null) {
            return 0L;
        }
        return (long) gauge.value();
    }

    private HikariDataSource pool() throws java.sql.SQLException {
        // The pooled connections default to the Supabase schema, so it has to exist first.
        try (var connection = java.sql.DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("benchmark-supabase");
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setSchema(SCHEMA);
        config.setMaximumPoolSize(10);
        return new HikariDataSource(config);
    }

    private RequestEventBatchWriter transactionalWriter(HikariDataSource dataSource,
            NamedParameterJdbcTemplate jdbc) {
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        RequestEventBatchWriter delegate = new RequestEventBatchWriter(
                new JdbcRequestEventRepository(jdbc),
                new JdbcSecurityEventRepository(jdbc),
                new NoOpPublisher());
        // Reproduce the @Transactional boundary the Spring proxy would add.
        return new RequestEventBatchWriter(null, null, null) {
            @Override
            public void write(TelemetryBatch batch) {
                transactions.executeWithoutResult(status -> delegate.write(batch));
            }
        };
    }

    /** Metric names mirrored from RequestEventService, which keeps them package-private. */
    private static final class RequestEventServiceMetrics {
        static final String FRESH_QUEUE = "request_events.queue.fresh.size";
        static final String RETRY_QUEUE = "request_events.queue.retry.size";
        static final String DROPPED = "request_events.dropped.total";
        static final String SECURITY_DROPPED = "security_events.dropped.total";
        static final String FLUSH_SUCCESS = "request_events.flush.success";
        static final String FLUSH_FAILURE = "request_events.flush.failure";
        static final String FLUSH_LATENCY = "request_events.flush.latency";
        static final String RETRY = "request_events.retry.total";
        static final String ABANDONED = "request_events.batch.abandoned";
        static final String POISON = "request_events.poison.total";
        static final String CONCURRENT_SKIPPED = "request_events.flush.concurrent_skipped";
    }

    private static final class NoOpPublisher extends AdminSecurityRequestEventPublisher {
        NoOpPublisher() {
            super(null, null);
        }

        @Override
        public void publishAfterCommit(com.vibegraph.abuse.RequestEvent event) {
            // The SSE stream is out of scope for pipeline capacity.
        }
    }

    private static final class BenchmarkResult {
        int producerThreads;
        long targetRatePerSecond;
        long eventsOffered;
        int queueCapacity;
        int batchSize;
        int freshBatchesPerCycle;
        long flushIntervalMs;
        long arrivalMillis;
        long drainMillis;
        long peakFreshQueue;
        long peakRetryQueue;
        long dropped;
        long securityDropped;
        long flushSuccess;
        long flushFailure;
        long retries;
        long abandoned;
        long poison;
        long concurrentSkipped;
        double meanFlushLatencyMs;
        double maxFlushLatencyMs;
        Long storedRows;
        Long totalRelationBytes;
        Long indexBytes;
        Double bytesPerEvent;

        /**
         * Rows actually persisted per second over the whole run. Deliberately measured, not
         * derived from {@code batchSize / flushInterval}.
         */
        double observedDrainRatePerSecond() {
            long stored = storedRows == null ? 0L : storedRows;
            return stored / (Math.max(arrivalMillis + drainMillis, 1) / 1000.0);
        }

        Map<String, Object> toMap() {
            double arrivalRate = eventsOffered / Math.max(arrivalMillis / 1000.0, 0.001);
            long stored = storedRows == null ? 0L : storedRows;
            double drainRate = observedDrainRatePerSecond();

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("producerThreads", producerThreads);
            values.put("targetRatePerSecond", targetRatePerSecond == 0 ? "unpaced" : targetRatePerSecond);
            values.put("eventsOffered", eventsOffered);
            values.put("eventsStored", stored);
            values.put("queueCapacity", queueCapacity);
            values.put("batchSize", batchSize);
            values.put("flushIntervalMs", flushIntervalMs);
            values.put("freshBatchesPerCycle", freshBatchesPerCycle);
            values.put("arrivalMillis", arrivalMillis);
            values.put("drainMillis", drainMillis);
            values.put("arrivalRatePerSecond", round(arrivalRate));
            values.put("observedDrainRatePerSecond", round(drainRate));
            values.put("drainToArrivalRatio", round(drainRate / Math.max(arrivalRate, 0.001)));
            values.put("peakFreshQueue", peakFreshQueue);
            values.put("peakRetryQueue", peakRetryQueue);
            values.put("freshQueueUtilization", round((double) peakFreshQueue / Math.max(queueCapacity, 1)));
            values.put("droppedEvents", dropped);
            values.put("droppedSecurityEvents", securityDropped);
            values.put("flushSuccess", flushSuccess);
            values.put("flushFailure", flushFailure);
            values.put("retries", retries);
            values.put("abandonedBatches", abandoned);
            values.put("poisonEvents", poison);
            values.put("concurrentFlushSkipped", concurrentSkipped);
            values.put("meanFlushLatencyMs", round(meanFlushLatencyMs));
            values.put("maxFlushLatencyMs", round(maxFlushLatencyMs));
            values.put("totalRelationBytes", totalRelationBytes == null ? 0L : totalRelationBytes);
            values.put("indexBytes", indexBytes == null ? 0L : indexBytes);
            values.put("bytesPerEvent", bytesPerEvent == null ? null : round(bytesPerEvent));
            values.put("postgresImage", "postgres:16-alpine");
            values.put("note", "Local container measurement. Not a production capacity conclusion: "
                    + "no managed Supabase instance and no network latency.");
            return values;
        }

        private static Object round(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return Math.round(value * 100.0) / 100.0;
        }
    }
}
