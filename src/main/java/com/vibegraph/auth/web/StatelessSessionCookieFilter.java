package com.vibegraph.auth.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Removes servlet session cookies from the stateless JWT browser flow. */
@Component
public class StatelessSessionCookieFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "JSESSIONID";
    private static final String SAME_SITE = "Lax";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        if (shouldClearSessionCookie(request, response) && !response.isCommitted()) {
            response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie(request).toString());
        }
    }

    private boolean shouldClearSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        return hasIncomingSessionCookie(request) || response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(header -> header.startsWith(COOKIE_NAME + "="));
    }

    private boolean hasIncomingSessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        return Arrays.stream(request.getCookies()).anyMatch(cookie -> COOKIE_NAME.equals(cookie.getName()));
    }

    private ResponseCookie expiredCookie(HttpServletRequest request) {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite(SAME_SITE)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.equalsIgnoreCase("https");
    }
}
