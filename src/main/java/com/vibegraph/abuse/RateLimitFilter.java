package com.vibegraph.abuse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RateLimitFilter extends OncePerRequestFilter {

    private final AbuseProperties properties;
    private final ClientAddressResolver addressResolver;
    private final RequestEventService eventService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AbuseProperties properties, ClientAddressResolver addressResolver,
            RequestEventService eventService, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.addressResolver = addressResolver;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String ip = addressResolver.resolve(request);
        PrincipalInfo principal = principal(request);
        String route = route(request);
        Instant started = clock.instant();
        boolean allowed = consume("ip:" + ip, properties.getRequestsPerMinutePerIp());
        if (allowed && principal.userId() != null) {
            allowed = consume("user:" + principal.userId(), properties.getRequestsPerMinutePerUser());
        }
        if (allowed && principal.apiKeyRef() != null) {
            allowed = consume("key:" + principal.apiKeyRef(), properties.getRequestsPerMinutePerApiKey());
        }
        if (!allowed) {
            writeError(response, 429, "TOO_MANY_REQUESTS", "Request rate limit exceeded");
            safeRecord(principal, ip, route, request.getMethod(), 429, started, "RATE_LIMIT");
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            safeRecord(principal, ip, route, request.getMethod(), response.getStatus(), started, "REQUEST");
        }
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
        Window window = windows.compute(key, (ignored, previous) -> {
            if (previous == null || previous.minute() != minute) {
                return new Window(minute, 1);
            }
            return new Window(minute, previous.count() + 1);
        });
        return window.count() <= limit;
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

    private String route(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || path.isBlank() ? "/" : path.replaceAll("/[0-9a-fA-F-]{36}", "/{id}");
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                ErrorResponse.builder().code(code).message(message).build()));
    }

    private record Window(long minute, int count) {}
    private record PrincipalInfo(UUID userId, String apiKeyRef) {}
}
