package com.vibegraph.common.config;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Requires the {@code X-API-Key} header on filesystem-touching endpoints when a key is configured.
 *
 * <p>Guards the project-import and directory-browse endpoints, which drive server-side file access:
 * {@code POST /api/projects/import-local|import-archive|import-github} and
 * {@code GET /api/projects/browse}. Read-only graph/diagram endpoints are intentionally left open.
 *
 * <p>The gate is a no-op when {@link ApiKeyProperties#isEnabled()} is false (blank key), so dev and
 * the test suite run without a key. See {@link ApiKeyProperties} for the security caveats.
 */
@Component
@Order(1)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";

    private final ApiKeyProperties properties;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(ApiKeyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !isGuarded(request);
    }

    /** Filesystem-touching endpoints that must carry the key when the gate is enabled. */
    private boolean isGuarded(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method)) {
            return uri.equals("/api/projects/import-local")
                    || uri.equals("/api/projects/import-archive")
                    || uri.equals("/api/projects/import-github");
        }
        if ("GET".equals(method)) {
            return uri.equals("/api/projects/browse");
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(provided, properties.getApiKey())) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(ErrorResponse.builder()
                .code("UNAUTHORIZED")
                .message("A valid X-API-Key header is required for this endpoint.")
                .build());
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** Length-aware constant-time comparison to avoid leaking the key via timing. */
    private boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(ab, bb);
    }
}
