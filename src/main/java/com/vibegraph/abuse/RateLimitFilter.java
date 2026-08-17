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
import io.micrometer.core.instrument.Tags;

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

    /** Set once a stage has written the telemetry event, so the other stage does not duplicate it. */
    static final String RECORDED_ATTRIBUTE = "vibegraph.rateLimit.recorded";

    private static final long CAPACITY_WARNING_INTERVAL = 1_000L;

    /**
     * Which buckets a filter instance enforces. Two instances are needed because the buckets need
     * different positions in the chain:
     *
     * <ul>
     *   <li>{@link #EDGE} runs BEFORE the authentication filters so a wrong API key cannot burn
     *       ~5 BCrypt rounds before the limiter sees it (H13). At that point the request has no
     *       identity yet, so only the IP bucket can be enforced.
     *   <li>{@link #IDENTITY} runs AFTER them, because the user id lives in the SecurityContext
     *       that {@code JwtAuthFilter} populates and the API-key ref in the request attribute that
     *       {@code ApiKeyAuthFilter} sets. Enforcing those buckets at the edge silently disabled
     *       them: both values were always null, so per-user and per-API-key limits never applied.
     * </ul>
     *
     * <p>The two stages never share a window key ({@code ip:} vs {@code user:}/{@code key:}), so
     * separate window caches are correct; meters are tagged with the stage to stay distinguishable.
     */
    public enum Stage {
        EDGE("edge"),
        IDENTITY("identity");

        private final String tag;

        Stage(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    private final Stage stage;
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
        this(Stage.EDGE, properties, addressResolver, eventService, objectMapper, clock, meterRegistry);
    }

    public RateLimitFilter(Stage stage, AbuseProperties properties,
            ClientAddressResolver addressResolver, RequestEventService eventService,
            ObjectMapper objectMapper, Clock clock, MeterRegistry meterRegistry) {
        this.stage = stage;
        this.properties = properties;
        this.addressResolver = addressResolver;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        // Counters stay untagged on purpose: both stages increment the same series, so
        // `rate_limit.requests.rejected` keeps meaning "requests this limiter rejected" and any
        // existing dashboard or alert on it keeps working. Only the window-size gauge is tagged,
        // because each stage owns a separate cache and two same-named gauges would collide.
        this.evictions = meterRegistry.counter(WINDOW_EVICTION_METRIC);
        this.rejected = meterRegistry.counter(REJECTED_METRIC);
        this.capacityPressure = meterRegistry.counter(CAPACITY_PRESSURE_METRIC);
        this.windows = buildWindowCache(properties.getRateLimit(), clock);
        meterRegistry.gauge(WINDOW_SIZE_METRIC, Tags.of("stage", stage.tag()),
                this.windows, Cache::estimatedSize);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String ip = addressResolver.resolve(request);
        String method = request.getMethod();
        Instant started = clock.instant();

        if (!allowedForStage(request, ip)) {
            rejected.increment();
            writeError(response, 429, "TOO_MANY_REQUESTS", "Request rate limit exceeded");
            // RATE_LIMIT means a rate-limit stage rejected the request. A 429 produced further
            // downstream stays a plain REQUEST event.
            request.setAttribute(RECORDED_ATTRIBUTE, Boolean.TRUE);
            safeRecord(request, ip, RequestTelemetryNormalizer.resolveRoute(request), method,
                    429, started, "RATE_LIMIT");
            return;
        }

        // Only the edge stage owns telemetry, otherwise every request would be recorded twice.
        if (stage != Stage.EDGE) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean recorded = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error ex) {
            recorded = true;
            safeRecord(request, ip, RequestTelemetryNormalizer.resolveRoute(request), method,
                    statusAfterFailure(response), started, "REQUEST");
            throw ex;
        } finally {
            // The identity stage already wrote a RATE_LIMIT event for a request it rejected;
            // recording again here would double-count it as a plain REQUEST.
            if (!recorded && !Boolean.TRUE.equals(request.getAttribute(RECORDED_ATTRIBUTE))) {
                safeRecord(request, ip, RequestTelemetryNormalizer.resolveRoute(request), method,
                        response.getStatus(), started, "REQUEST");
            }
        }
    }

    /**
     * Consumes the buckets this stage is responsible for. The split exists because identity is not
     * known yet at the edge — see {@link Stage}.
     */
    private boolean allowedForStage(HttpServletRequest request, String ip) {
        if (stage == Stage.EDGE) {
            return consume("ip:" + ip, properties.getRequestsPerMinutePerIp());
        }
        PrincipalInfo principal = principal(request);
        if (principal.userId() != null
                && !consume("user:" + principal.userId(), properties.getRequestsPerMinutePerUser())) {
            return false;
        }
        return principal.apiKeyRef() == null
                || consume("key:" + principal.apiKeyRef(), properties.getRequestsPerMinutePerApiKey());
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

    /**
     * Resolves the principal at record time rather than at filter entry.
     *
     * <p>The edge stage runs before the authentication filters, so reading the principal on the way
     * in always yielded {@code null} for both the user id and the API-key ref — telemetry lost all
     * attribution. Reading it here works because this runs after the inner chain (and therefore
     * after {@code JwtAuthFilter}/{@code ApiKeyAuthFilter}) has populated them, while Spring
     * Security's context-clearing filter still sits outside this one.
     */
    private void safeRecord(HttpServletRequest request, String ip, String route, String method,
            int status, Instant timestamp, String eventType) {
        try {
            PrincipalInfo principal = principal(request);
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
