package com.vibegraph.auth.web;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a custom-header CSRF boundary for requests authenticated by browser cookies. */
@Component
public class CookieCsrfFilter extends OncePerRequestFilter {

    private static final String CLIENT_HEADER = "X-VibeGraph-Client";
    private static final String CLIENT_VALUE = "web";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (requiresHeader(request) && !isTrustedBrowserRequest(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.<Void>error(
                    ErrorResponse.builder()
                            .code("CSRF_VALIDATION_FAILED")
                            .message("Browser authentication header is required")
                            .build()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean requiresHeader(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            return false;
        }
        // SockJS transport POSTs cannot carry the application's custom browser header.
        // Authentication and per-project authorization are enforced by the STOMP
        // interceptor after CONNECT/SUBSCRIBE, so do not apply REST cookie-CSRF here.
        if (request.getRequestURI().startsWith("/ws/")) {
            return false;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieService.COOKIE_NAME.equals(cookie.getName())
                    || AuthCookieService.REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTrustedBrowserRequest(HttpServletRequest request) {
        return CLIENT_VALUE.equalsIgnoreCase(request.getHeader(CLIENT_HEADER));
    }
}
