package com.vibegraph.auth.web;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vibegraph.auth.domain.ApiKey;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.AuthenticatedUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final int MAX_PREFIX_CANDIDATES = 5;
    /**
     * F6 audit fix: {@code lastUsedAt} is an operational display value, not a per-request
     * fact. Writing it on every API-key request amplified DB writes and created row-lock
     * contention on {@code api_keys} under load; throttle to at most one write per key
     * per interval instead.
     */
    static final Duration LAST_USED_WRITE_INTERVAL = Duration.ofSeconds(60);
    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String API_KEY_REF_ATTRIBUTE = "vibegraph.apiKeyRef";
    public static final String API_KEY_CONTEXT_ATTRIBUTE = "vibegraph.apiKeyContext";

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ProjectOwnershipRepository projectOwnershipRepository;

    private final AccountSettingsService accountSettingsService;
    private final PasswordEncoder passwordEncoder;
    // Remembers the last persisted lastUsedAt per key so repeat requests inside the
    // throttle window skip the DB write; entries expire once they can no longer gate one.
    private final Cache<UUID, Instant> lastUsedWrites = Caffeine.newBuilder()
            .expireAfterWrite(LAST_USED_WRITE_INTERVAL.multipliedBy(5))
            .build();

    public ApiKeyAuthFilter(
            ApiKeyRepository apiKeyRepository,
            UserRepository userRepository,
            ProjectOwnershipRepository projectOwnershipRepository,
            AccountSettingsService accountSettingsService,
            PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.projectOwnershipRepository = projectOwnershipRepository;
        this.accountSettingsService = accountSettingsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!supportsApiKeyAuthentication(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String presented = request.getHeader(API_KEY_HEADER);
        if (presented == null || presented.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            SecurityContextHolder.clearContext();
            java.util.Optional<ApiKey> match = findMatch(presented.trim());
            if (match.isEmpty() || !authenticate(request, match.get())) {
                writeUnauthorized(response);
                return;
            }
            filterChain.doFilter(request, response);
        } catch (com.vibegraph.common.exception.AccountBlockedException ex) {
            writeBlocked(response, ex.getSafeReason());
        }
    }

    private java.util.Optional<ApiKey> findMatch(String presented) {
        if (!presented.startsWith("vbg_") || presented.length() < 12) {
            return java.util.Optional.empty();
        }
        String prefix = presented.substring(0, 12);
        List<ApiKey> candidates = apiKeyRepository
                .findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(prefix);
        if (candidates.size() > MAX_PREFIX_CANDIDATES) {
            return java.util.Optional.empty();
        }
        Instant now = Instant.now();
        return candidates.stream()
                .filter(key -> key.getDeletedAt() == null && key.getDisabledAt() == null)
                .filter(key -> key.getExpiresAt() == null || key.getExpiresAt().isAfter(now))
                .filter(key -> passwordEncoder.matches(presented, key.getKeyHash()))
                .findFirst();
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\","
                + "\"message\":\"Invalid API key\"}}");
    }

    private void writeBlocked(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"ACCOUNT_BLOCKED\",\"message\":\""
                + jsonEscape(reason) + "\"}}");
    }

    private String jsonEscape(String value) {
        return value == null ? "Account is blocked" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean supportsApiKeyAuthentication(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/mcp")
                || path.matches("/api/projects/[^/]+/patch")
                || path.equals("/api/projects/current/patch");
    }

    private boolean hasValidProjectBinding(ApiKey key) {
        if (key.getProjectId() == null) {
            return false;
        }
        if (projectOwnershipRepository == null) {
            return false;
        }
        return projectOwnershipRepository.findOwnerId(key.getProjectId())
                .filter(key.getUserId()::equals)
                .isPresent();
    }

    private boolean authenticate(HttpServletRequest request, ApiKey key) {
        if (!hasValidProjectBinding(key)) {
            return false;
        }
        return userRepository.findById(key.getUserId()).filter(user -> !user.isDeactivated()).map(user -> {
            accountSettingsService.assertNotBlocked(user.getId());
            AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()),
                            new SimpleGrantedAuthority("API_KEY")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String keyRef = key.getId() + ":" + key.getKeyPrefix();
            request.setAttribute(API_KEY_REF_ATTRIBUTE, keyRef);
            request.setAttribute(API_KEY_CONTEXT_ATTRIBUTE, new ApiKeyRequestContext(keyRef, key.getProjectId()));
            recordLastUsed(key);
            return true;
        }).orElse(false);
    }

    private void recordLastUsed(ApiKey key) {
        Instant now = Instant.now();
        Instant lastWrite = lastUsedWrites.getIfPresent(key.getId());
        if (lastWrite != null && Duration.between(lastWrite, now).compareTo(LAST_USED_WRITE_INTERVAL) < 0) {
            return;
        }
        key.setLastUsedAt(now);
        apiKeyRepository.save(key);
        lastUsedWrites.put(key.getId(), now);
    }
}
