package com.vibegraph.auth.service;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.vibegraph.auth.config.JwtProperties;

import jakarta.servlet.http.HttpServletRequest;

/** Builds the browser-facing HttpOnly JWT session cookie. */
@Service
public class AuthCookieService {

    public static final String COOKIE_NAME = "vg_session";
    public static final String REFRESH_COOKIE_NAME = "vg_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final long accessExpirationMs;
    private final long refreshExpirationMs;
    private final boolean secureCookies;
    private final String sameSite;

    public AuthCookieService(JwtProperties properties) {
        this.accessExpirationMs = positiveDuration(properties.getExpirationMs(), "access");
        this.refreshExpirationMs = positiveDuration(properties.getRefreshExpirationMs(), "refresh");
        this.secureCookies = properties.isSecureCookies();
        this.sameSite = normalizeSameSite(properties.getSameSite());
    }

    public ResponseCookie sessionCookie(String token, HttpServletRequest request) {
        return baseCookie(COOKIE_NAME, token, "/", request)
                .maxAge(Duration.ofMillis(accessExpirationMs))
                .build();
    }

    public ResponseCookie clearCookie(HttpServletRequest request) {
        return baseCookie(COOKIE_NAME, "", "/", request)
                .maxAge(Duration.ZERO)
                .build();
    }

    /** Build the seven-day rotating refresh cookie. */
    public ResponseCookie refreshCookie(String token, HttpServletRequest request) {
        return baseCookie(REFRESH_COOKIE_NAME, token, REFRESH_COOKIE_PATH, request)
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build();
    }

    /** Expire the refresh cookie while preserving its restricted path. */
    public ResponseCookie clearRefreshCookie(HttpServletRequest request) {
        return baseCookie(REFRESH_COOKIE_NAME, "", REFRESH_COOKIE_PATH, request)
                .maxAge(Duration.ZERO)
                .build();
    }

    /** Read the raw refresh token from the request cookie. */
    public String refreshToken(HttpServletRequest request) {
        return cookieValue(request, REFRESH_COOKIE_NAME);
    }

    /** Read the access token from the request cookie for logout fallback. */
    public String sessionToken(HttpServletRequest request) {
        return cookieValue(request, COOKIE_NAME);
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(
            String name, String value, String path, HttpServletRequest request) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookies || isSecureRequest(request))
                .sameSite(sameSite)
                .path(path);
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request != null && request.isSecure()) {
            return true;
        }
        String forwardedProto = request == null ? null : request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.equalsIgnoreCase("https");
    }

    private String cookieValue(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(cookie -> cookie.getValue())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private long positiveDuration(long value, String name) {
        if (value <= 0) {
            throw new IllegalStateException("JWT " + name + " expiration must be positive");
        }
        return value;
    }

    private String normalizeSameSite(String value) {
        if (value == null || value.isBlank()) {
            return "Lax";
        }
        if ("Lax".equalsIgnoreCase(value)) {
            return "Lax";
        }
        if ("Strict".equalsIgnoreCase(value)) {
            return "Strict";
        }
        if ("None".equalsIgnoreCase(value)) {
            return "None";
        }
        throw new IllegalStateException("AUTH_COOKIE_SAME_SITE must be Lax, Strict, or None");
    }
}
