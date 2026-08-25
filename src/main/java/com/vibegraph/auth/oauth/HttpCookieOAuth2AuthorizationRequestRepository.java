package com.vibegraph.auth.oauth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Stores the short-lived OAuth authorization request in an HttpOnly cookie. */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "vg_oauth2_auth_request";
    private static final int COOKIE_MAX_AGE_SECONDS = 180;
    private static final int MAX_PENDING_REQUESTS = 4096;
    private static final int NONCE_BYTES = 32;
    private static final String SAME_SITE = "Lax";
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Map<String, PendingRequest> pending = new ConcurrentHashMap<>();

    public HttpCookieOAuth2AuthorizationRequestRepository() {
        this(Clock.systemUTC());
    }

    HttpCookieOAuth2AuthorizationRequestRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return findCookie(request).map(Cookie::getValue).flatMap(this::load).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            expireCookie(request, response);
            return;
        }
        evictExpired();
        if (pending.size() >= MAX_PENDING_REQUESTS) {
            evictOldest();
        }
        String nonce = nonce();
        pending.put(nonce, new PendingRequest(authorizationRequest,
                Instant.now(clock).plusSeconds(COOKIE_MAX_AGE_SECONDS)));
        ResponseCookie cookie = baseCookie(nonce, request)
                .maxAge(COOKIE_MAX_AGE_SECONDS)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = findCookie(request)
                .map(Cookie::getValue)
                .flatMap(this::remove)
                .orElse(null);
        expireCookie(request, response);
        return authorizationRequest;
    }

    private Optional<Cookie> findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .findFirst();
    }

    private Optional<OAuth2AuthorizationRequest> load(String nonce) {
        if (!validNonce(nonce)) {
            return Optional.empty();
        }
        PendingRequest value = pending.get(nonce);
        if (value == null || value.expiresAt().isBefore(Instant.now(clock))) {
            pending.remove(nonce, value);
            return Optional.empty();
        }
        return Optional.of(value.request());
    }

    private Optional<OAuth2AuthorizationRequest> remove(String nonce) {
        if (!validNonce(nonce)) {
            return Optional.empty();
        }
        PendingRequest value = pending.remove(nonce);
        if (value == null || value.expiresAt().isBefore(Instant.now(clock))) {
            return Optional.empty();
        }
        return Optional.of(value.request());
    }

    private String nonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean validNonce(String value) {
        return value != null && value.length() <= 128 && value.matches("[A-Za-z0-9_-]{43}");
    }

    private void evictExpired() {
        Instant now = Instant.now(clock);
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private void evictOldest() {
        pending.entrySet().stream().min(Map.Entry.comparingByValue((a, b) ->
                a.expiresAt().compareTo(b.expiresAt()))).ifPresent(entry -> pending.remove(entry.getKey()));
    }

    private void expireCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = baseCookie("", request)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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

    private record PendingRequest(OAuth2AuthorizationRequest request, Instant expiresAt) { }
}
