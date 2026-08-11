package com.vibegraph.benchmark;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.abuse.AbuseProperties;
import com.vibegraph.abuse.ClientAddressResolver;
import com.vibegraph.abuse.RateLimitFilter;
import com.vibegraph.abuse.RequestEventService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.mockito.Mockito.mock;

/**
 * Measures the rate-limit window cache under IP cardinality pressure.
 *
 * <p>Opt-in only: {@code mvn test} does not match {@code *Benchmark} classes. Re-run with:
 *
 * <pre>mvn -o -Pbenchmark test</pre>
 *
 * <p>No database and no network are involved, so this one runs anywhere.
 */
@DisplayName("Rate-limit cardinality benchmark")
class RateLimitCardinalityBenchmark {

    private static final int THREADS = 8;
    private static final int DISTINCT_KEYS_PER_THREAD = 25_000;
    private static final int MAXIMUM_WINDOWS = 50_000;

    @Test
    @DisplayName("measure window cache size, eviction and throughput under high cardinality")
    void measureWindowCacheUnderCardinalityPressure() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(1_000_000);
        properties.getRateLimit().setWindowMaximumSize(MAXIMUM_WINDOWS);
        RateLimitFilter filter = new RateLimitFilter(properties, new ClientAddressResolver(properties),
                mock(RequestEventService.class), new ObjectMapper(), Clock.systemUTC(), registry);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        for (int thread = 0; thread < THREADS; thread++) {
            final int group = thread;
            pool.submit(() -> {
                start.await();
                for (int index = 0; index < DISTINCT_KEYS_PER_THREAD; index++) {
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
                    request.setRemoteAddr(address(group, index));
                    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
                }
                return null;
            });
        }
        long startedAt = System.nanoTime();
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.MINUTES)) {
            pool.shutdownNow();
            throw new IllegalStateException("rate-limit producers did not finish in time");
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        long requests = (long) THREADS * DISTINCT_KEYS_PER_THREAD;
        Map<String, Object> measurements = new LinkedHashMap<>();
        measurements.put("threads", THREADS);
        measurements.put("distinctKeys", requests);
        measurements.put("configuredMaximumWindows", MAXIMUM_WINDOWS);
        measurements.put("elapsedMillis", elapsedMillis);
        measurements.put("requestsPerSecond", Math.round(requests / Math.max(elapsedMillis / 1000.0, 0.001)));
        measurements.put("trackedWindowsAfterCleanup", trackedWindows(filter));
        measurements.put("windowEvictions", (long) registry.counter("rate_limit.windows.evictions").count());
        measurements.put("capacityPressureEvents", (long) registry.counter("rate_limit.capacity.pressure").count());
        measurements.put("rejectedRequests", (long) registry.counter("rate_limit.requests.rejected").count());
        measurements.put("note", "Per-instance enforcement. Under capacity pressure the cache evicts "
                + "least-recently-used windows and enforcement becomes best-effort for those keys.");

        BenchmarkReport.write("rate-limit-cardinality", measurements);
    }

    /** trackedWindows() is package-private on the filter, so reach it reflectively from here. */
    private long trackedWindows(RateLimitFilter filter) throws Exception {
        var method = RateLimitFilter.class.getDeclaredMethod("trackedWindows");
        method.setAccessible(true);
        return (long) method.invoke(filter);
    }

    private static String address(int group, int index) {
        return String.format("10.%d.%d.%d", group % 256, (index / 256) % 256, index % 256);
    }
}
