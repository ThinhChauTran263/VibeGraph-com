package com.vibegraph.abuse;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-instance fixed-window rate limiter.
 *
 * <p>Windows live in a bounded TTL cache rather than an unbounded map, so a hostile or simply
 * high-cardinality stream of IPs, users or API keys cannot grow the heap without limit. The
 * trade-off is explicit: once the cache reaches its configured maximum, the least recently used
 * windows are evicted and enforcement becomes best-effort for the evicted keys. That is signalled
 * through {@code rate_limit.capacity.pressure} and a rate-limited warning that never contains the
 * key itself.
 *
 * <p>Enforcement is per instance only. Running N replicas multiplies the effective allowance by N;
 * this filter is not a cluster-wide limiter.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    static final String WINDOW_SIZE_METRIC = "rate_limit.windows.size";
    static final String WINDOW_EVICTION_METRIC = "rate_limit.windows.evictions";
    static final String REJECTED_METRIC = "rate_limit.requests.rejected";
    static final String CAPACITY_PRESSURE_METRIC = "rate_limit.capacity.pressure";

    private static final long CAPACITY_WARNING_INTERVAL = 1_000L;

    private final AbuseProperties properties;
    private final ClientAddressResolver addressResolver;
    private final RequestEventService eventService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Cache<String, Window> windows;
    private final Counter evictions;
    private final Counter rejected;
    private final Counter capacityPressure;
    private final AtomicLong capacityEvictionCount = new AtomicLong();

    public RateLimitFilter(AbuseProperties properties, ClientAddressResolver addressResolver,
            RequestEventService eventService, ObjectMapper objectMapper, Clock clock,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.addressResolver = addressResolver;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.evictions = meterRegistry.counter(WINDOW_EVICTION_METRIC);
        this.rejected = meterRegistry.counter(REJECTED_METRIC);
        this.capacityPressure = meterRegistry.counter(CAPACITY_PRESSURE_METRIC);
        this.windows = buildWindowCache(properties.getRateLimit(), clock);
        meterRegistry.gauge(WINDOW_SIZE_METRIC, this.windows, Cache::estimatedSize);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String ip = addressResolver.resolve(request);
        PrincipalInfo principal = principal(request);
        String method = request.getMethod();
        Instant started = clock.instant();
        boolean allowed = consume("ip:" + ip, properties.getRequestsPerMinutePerIp());
        if (allowed && principal.userId() != null) {
            allowed = consume("user:" + principal.userId(), properties.getRequestsPerMinutePerUser());
        }
        if (allowed && principal.apiKeyRef() != null) {
            allowed = consume("key:" + principal.apiKeyRef(), properties.getRequestsPerMinutePerApiKey());
        }
        if (!allowed) {
            rejected.increment();
            writeError(response, 429, "TOO_MANY_REQUESTS", "Request rate limit exceeded");
            // RATE_LIMIT means this filter rejected the request. A 429 produced further
            // downstream stays a plain REQUEST event.
            safeRecord(principal, ip, RequestTelemetryNormalizer.resolveRoute(request), method,
                    429, started, "RATE_LIMIT");
            return;
        }
        boolean recorded = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error ex) {
            recorded = true;
            safeRecord(principal, ip, RequestTelemetryNormalizer.resolveRoute(request), method,
                    statusAfterFailure(response), started, "REQUEST");
            throw ex;
        } finally {
            if (!recorded) {
                safeRecord(principal, ip, RequestTelemetryNormalizer.resolveRoute(request), method,
                        response.getStatus(), started, "REQUEST");
            }
        }
    }

    /**
     * An exception escaping the chain means the container will render an error page. The status on
     * the response is still the default 200 unless something already committed an error, so record
     * a server error rather than a misleading success.
     */
    private int statusAfterFailure(HttpServletResponse response) {
        int status = response.getStatus();
        return status >= 400 ? status : 500;
    }

    private void safeRecord(PrincipalInfo principal, String ip, String route, String method,
            int status, Instant timestamp, String eventType) {
        try {
            eventService.record(principal.userId(), principal.apiKeyRef(), ip, route,
                    method, status, timestamp, eventType);
        } catch (RuntimeException ignored) {
            // Telemetry failures must not alter the application response.
        }
    }

    private boolean consume(String key, int limit) {
        if (limit <= 0) {
            return true;
        }
        long minute = clock.millis() / 60_000L;
        Window window = windows.asMap().compute(key, (ignored, previous) -> {
            if (previous == null || previous.minute() != minute) {
                return new Window(minute, 1);
            }
            return new Window(minute, previous.count() + 1);
        });
        return window.count() <= limit;
    }

    private Cache<String, Window> buildWindowCache(AbuseProperties.RateLimit rateLimit, Clock clock) {
        return Caffeine.newBuilder()
                .maximumSize(rateLimit.getWindowMaximumSize())
                .expireAfterWrite(Duration.ofMillis(rateLimit.getWindowTtlMs()))
                // Drive expiry from the injected clock so window ageing stays deterministic.
                .ticker(() -> clock.millis() * 1_000_000L)
                .executor(Runnable::run)
                .evictionListener((key, value, cause) -> onEviction(cause))
                .build();
    }

    private void onEviction(RemovalCause cause) {
        if (!cause.wasEvicted()) {
            return;
        }
        evictions.increment();
        if (cause != RemovalCause.SIZE) {
            return;
        }
        capacityPressure.increment();
        long count = capacityEvictionCount.incrementAndGet();
        if (count % CAPACITY_WARNING_INTERVAL == 1) {
            log.warn("Rate-limit window cache is at its configured maximum of {} entries; "
                    + "enforcement is best-effort for evicted keys ({} capacity evictions so far)",
                    properties.getRateLimit().getWindowMaximumSize(), count);
        }
    }

    private PrincipalInfo principal(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String apiKeyRef = request.getAttribute(com.vibegraph.auth.web.ApiKeyAuthFilter.API_KEY_REF_ATTRIBUTE)
                instanceof String ref ? ref : null;
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return new PrincipalInfo(user.id(), apiKeyRef);
        }
        return new PrincipalInfo(null, apiKeyRef);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                ErrorResponse.builder().code(code).message(message).build()));
    }

    /** Runs pending maintenance and returns the current window count. Test seam. */
    long trackedWindows() {
        windows.cleanUp();
        return windows.estimatedSize();
    }

    private record Window(long minute, int count) {}
    private record PrincipalInfo(UUID userId, String apiKeyRef) {}
}
