package com.vibegraph.auth.service;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.vibegraph.auth.config.JwtProperties;

import jakarta.servlet.http.HttpServletRequest;

/** Builds the browser-facing HttpOnly JWT session cookie. */
@Service
public class AuthCookieService {

    public static final String COOKIE_NAME = "vg_session";
    private static final String SAME_SITE = "Lax";

    private final long expirationMs;

    public AuthCookieService(JwtProperties properties) {
        this.expirationMs = properties.getExpirationMs();
    }

    public ResponseCookie sessionCookie(String token, HttpServletRequest request) {
        return baseCookie(token, request)
                .maxAge(Duration.ofMillis(expirationMs))
                .build();
    }

    public ResponseCookie clearCookie(HttpServletRequest request) {
        return baseCookie("", request)
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value, HttpServletRequest request) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite(SAME_SITE)
                .path("/");
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request != null && request.isSecure()) {
            return true;
        }
        String forwardedProto = request == null ? null : request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.equalsIgnoreCase("https");
    }
}
