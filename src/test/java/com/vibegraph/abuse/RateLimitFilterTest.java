package com.vibegraph.abuse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void doFilter_requestsBeyondIpLimit_returnsStructured429() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(1);
        properties.setRequestsPerMinutePerUser(100);
        RequestEventService eventService = mock(RequestEventService.class);
        RateLimitFilter filter = filter(properties, eventService,
                Clock.fixed(Instant.parse("2026-07-16T10:00:00Z"), ZoneOffset.UTC));

        MockHttpServletRequest first = request("203.0.113.5");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());

        MockHttpServletRequest second = request("203.0.113.5");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        MockFilterChain secondChain = new MockFilterChain();
        filter.doFilter(second, secondResponse, secondChain);

        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getContentAsString()).contains("TOO_MANY_REQUESTS");
        assertThat(secondChain.getRequest()).isNull();
    }

    @Test
    void doFilter_spoofedForwardedFor_doesNotCreateAnotherDefaultBucket() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(1);
        RateLimitFilter filter = filter(properties, mock(RequestEventService.class), Clock.systemUTC());

        MockHttpServletRequest first = request("203.0.113.5");
        first.addHeader("X-Forwarded-For", "198.51.100.1");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = request("203.0.113.5");
        second.addHeader("X-Forwarded-For", "198.51.100.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("window cache stays within the configured maximum and reports capacity pressure")
    void consume_beyondMaximumWindows_evictsAndReportsCapacityPressure() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(1_000);
        properties.getRateLimit().setWindowMaximumSize(100);
        RateLimitFilter filter = filter(properties, mock(RequestEventService.class), Clock.systemUTC());

        for (int index = 0; index < 500; index++) {
            filter.doFilter(request(distinctIp(0, index)),
                    new MockHttpServletResponse(), new MockFilterChain());
        }

        assertThat(filter.trackedWindows()).isLessThanOrEqualTo(100L);
        assertThat(meterRegistry.counter(RateLimitFilter.WINDOW_EVICTION_METRIC).count()).isPositive();
        assertThat(meterRegistry.counter(RateLimitFilter.CAPACITY_PRESSURE_METRIC).count()).isPositive();
        assertThat(meterRegistry.get(RateLimitFilter.WINDOW_SIZE_METRIC).gauge().value())
                .isLessThanOrEqualTo(100.0);
    }

    @Test
    @DisplayName("windows expire after the configured TTL")
    void consume_afterWindowTtl_expiresIdleWindows() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(5);
        properties.getRateLimit().setWindowTtlMs(60_000);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T10:00:00Z"));
        RateLimitFilter filter = filter(properties, mock(RequestEventService.class), clock);

        filter.doFilter(request("203.0.113.9"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(filter.trackedWindows()).isOne();

        clock.advance(Duration.ofMinutes(5));

        assertThat(filter.trackedWindows()).isZero();
    }

    @Test
    @DisplayName("active windows keep counting correctly under the maximum")
    void consume_activeWindow_keepsCountingWithinLimit() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(3);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T10:00:00Z"));
        RateLimitFilter filter = filter(properties, mock(RequestEventService.class), clock);

        for (int index = 0; index < 3; index++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("203.0.113.11"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        filter.doFilter(request("203.0.113.11"), rejectedResponse, new MockFilterChain());
        assertThat(rejectedResponse.getStatus()).isEqualTo(429);
        assertThat(meterRegistry.counter(RateLimitFilter.REJECTED_METRIC).count()).isEqualTo(1.0);

        // A new minute resets the fixed window.
        clock.advance(Duration.ofMinutes(1));
        MockHttpServletResponse nextMinute = new MockHttpServletResponse();
        filter.doFilter(request("203.0.113.11"), nextMinute, new MockFilterChain());
        assertThat(nextMinute.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("api key limit is enforced independently of the ip limit")
    void consume_apiKeyLimit_enforcedIndependently() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(1_000);
        properties.setRequestsPerMinutePerApiKey(1);
        RateLimitFilter filter = filter(properties, mock(RequestEventService.class), Clock.systemUTC());

        MockHttpServletRequest first = request("203.0.113.20");
        first.setAttribute(com.vibegraph.auth.web.ApiKeyAuthFilter.API_KEY_REF_ATTRIBUTE, "key-1");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = request("203.0.113.21");
        second.setAttribute(com.vibegraph.auth.web.ApiKeyAuthFilter.API_KEY_REF_ATTRIBUTE, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("concurrent traffic under capacity pressure never throws")
    void consume_concurrentAccessUnderPressure_doesNotThrow() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(1_000_000);
        properties.getRateLimit().setWindowMaximumSize(100);
        RateLimitFilter filter = filter(properties, mock(RequestEventService.class), Clock.systemUTC());

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int thread = 0; thread < threads; thread++) {
            final int id = thread;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int index = 0; index < 500; index++) {
                        filter.doFilter(request(distinctIp(id, index)),
                                new MockHttpServletResponse(), new MockFilterChain());
                    }
                } catch (Throwable ex) {
                    failure.compareAndSet(null, ex);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(failure.get()).isNull();
        assertThat(filter.trackedWindows()).isLessThanOrEqualTo(100L);
    }

    @Test
    @DisplayName("matched handler routes are recorded as the handler template")
    void doFilter_matchedHandlerPattern_recordsTemplateRoute() throws Exception {
        RequestEventService eventService = mock(RequestEventService.class);
        RateLimitFilter filter = filter(new AbuseProperties(), eventService, Clock.systemUTC());

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/projects/2f1b3c14-1f2a-4c1e-9e59-0b1d2f3a4b5c");
        request.setRemoteAddr("203.0.113.30");
        FilterChain chain = (servletRequest, servletResponse) -> servletRequest.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/projects/{id}");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(eventService).record(isNull(), isNull(), eq("203.0.113.30"),
                eq("/api/projects/{id}"), eq("GET"), eq(200), any(), eq("REQUEST"));
    }

    @Test
    @DisplayName("unmatched routes fall back to a masked, query-free path")
    void doFilter_unmatchedRoute_masksIdentifiersAndDropsQueryString() throws Exception {
        RequestEventService eventService = mock(RequestEventService.class);
        RateLimitFilter filter = filter(new AbuseProperties(), eventService, Clock.systemUTC());

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/files/2f1b3c14-1f2a-4c1e-9e59-0b1d2f3a4b5c/42/user@example.com/deadbeefdeadbeef99");
        request.setQueryString("token=super-secret-value");
        request.setRemoteAddr("203.0.113.31");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(eventService).record(isNull(), isNull(), eq("203.0.113.31"),
                eq("/files/{id}/{id}/{email}/{token}"), eq("GET"), eq(200), any(), eq("REQUEST"));
    }

    @Test
    @DisplayName("an exception escaping the chain is recorded once as a server error and rethrown")
    void doFilter_unhandledException_recordsSingle500AndRethrows() throws Exception {
        RequestEventService eventService = mock(RequestEventService.class);
        RateLimitFilter filter = filter(new AbuseProperties(), eventService, Clock.systemUTC());
        IllegalStateException failure = new IllegalStateException("handler blew up");
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw failure;
        };

        assertThatThrownBy(() -> filter.doFilter(request("203.0.113.32"),
                new MockHttpServletResponse(), chain))
                .isSameAs(failure);

        verify(eventService, times(1)).record(isNull(), isNull(), anyString(), anyString(),
                anyString(), eq(500), any(), eq("REQUEST"));
        verify(eventService, never()).record(any(UUID.class), any(), anyString(), anyString(),
                anyString(), eq(200), any(), anyString());
    }

    @Test
    @DisplayName("a downstream 429 stays a REQUEST event, only this filter emits RATE_LIMIT")
    void doFilter_downstream429_isNotReportedAsRateLimit() throws Exception {
        RequestEventService eventService = mock(RequestEventService.class);
        RateLimitFilter filter = filter(new AbuseProperties(), eventService, Clock.systemUTC());
        FilterChain chain = (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(429);

        filter.doFilter(request("203.0.113.33"), new MockHttpServletResponse(), chain);

        verify(eventService).record(isNull(), isNull(), anyString(), anyString(), anyString(),
                eq(429), any(), eq("REQUEST"));
        verify(eventService, never()).record(any(), any(), anyString(), anyString(), anyString(),
                anyInt(), any(), eq("RATE_LIMIT"));
    }

    @Test
    @DisplayName("this filter's own rejection is recorded as RATE_LIMIT")
    void doFilter_ownRejection_isReportedAsRateLimit() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(1);
        RequestEventService eventService = mock(RequestEventService.class);
        RateLimitFilter filter = filter(properties, eventService, Clock.systemUTC());

        filter.doFilter(request("203.0.113.34"), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request("203.0.113.34"), new MockHttpServletResponse(), new MockFilterChain());

        verify(eventService).record(isNull(), isNull(), anyString(), anyString(), anyString(),
                eq(429), any(), eq("RATE_LIMIT"));
    }

    private RateLimitFilter filter(AbuseProperties properties, RequestEventService eventService,
            Clock clock) {
        return new RateLimitFilter(properties, new ClientAddressResolver(properties), eventService,
                new ObjectMapper(), clock, meterRegistry);
    }

    private static String distinctIp(int group, int index) {
        return "10." + (group % 256) + "." + (index / 256 % 256) + "." + (index % 256);
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr(remoteAddress);
        return request;
    }

}
