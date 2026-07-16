package com.vibegraph.auth.web;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;
import com.vibegraph.common.exception.AccountBlockedException;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Stateless token filter. Reads {@code Authorization: Bearer <jwt>} for CLI/API clients or the
 * browser {@code vg_session} cookie, verifies it, and populates the {@link SecurityContextHolder}
 * with an {@link AuthenticatedUser} principal and a {@code ROLE_*} authority.
 *
 * <p>On a missing or invalid token the filter does NOT reject directly — it leaves the context
 * unauthenticated and lets the chain continue, so the authorization rules + entry point produce
 * a consistent 401. It never throws to the client.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final java.util.Map<java.util.UUID, Long> activeUsers = new java.util.concurrent.ConcurrentHashMap<>();

    public static int getActiveUsersCount() {
        long threshold = System.currentTimeMillis() - 5 * 60 * 1000; // 5 minutes
        activeUsers.values().removeIf(t -> t < threshold);
        return activeUsers.size();
    }

    private final JwtService jwtService;
    private final AccountSettingsService accountSettingsService;
    private final com.vibegraph.auth.repository.UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = bearerToken(header);
        if (token == null) {
            token = cookieToken(request);
        }
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthenticatedUser principal = jwtService.parse(token);
                AccountBlockedException blocked = blockedException(principal.id());
                if (blocked != null && !isRestrictedAccountRoute(request)) {
                    SecurityContextHolder.clearContext();
                    writeRestrictedResponse(response, blocked.getSafeReason());
                    return;
                }

                var userOpt = userRepository.findById(principal.id());
                if (userOpt.isEmpty()) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                var user = userOpt.get();
                if (user.isDeactivated() && !isRestrictedAccountRoute(request)) {
                    SecurityContextHolder.clearContext();
                    writeRestrictedResponse(response, safeDeactivationReason(user.getDeactivationReasonSafe()));
                    return;
                }

                var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(authority));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                activeUsers.put(principal.id(), System.currentTimeMillis());
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid/expired/malformed token — stay unauthenticated; entry point returns 401.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String bearerToken(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieService.COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private AccountBlockedException blockedException(java.util.UUID userId) {
        try {
            accountSettingsService.assertNotBlocked(userId);
            return null;
        } catch (AccountBlockedException ex) {
            return ex;
        }
    }

    private boolean isRestrictedAccountRoute(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("GET".equals(method) && "/api/auth/me".equals(path)) {
            return true;
        }
        if ("GET".equals(method) && "/api/account/session-state".equals(path)) {
            return true;
        }
        if ("/api/account/reports".equals(path)) {
            return "GET".equals(method) || "POST".equals(method);
        }
        if (!path.matches("^/api/account/reports/[0-9a-fA-F-]{36}(/messages|/close)?$")) {
            return false;
        }
        if (path.endsWith("/messages")) {
            return "POST".equals(method);
        }
        if (path.endsWith("/close")) {
            return "PATCH".equals(method);
        }
        return "GET".equals(method);
    }

    private String safeDeactivationReason(String reason) {
        return reason == null || reason.isBlank() ? "Account closed by administrator" : reason;
    }

    private void writeRestrictedResponse(HttpServletResponse response, String safeReason)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = ErrorResponse.builder()
                .code("ACCOUNT_BLOCKED")
                .message(safeReason)
                .build();
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
    }
}
