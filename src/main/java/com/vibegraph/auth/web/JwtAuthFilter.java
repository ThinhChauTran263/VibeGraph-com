package com.vibegraph.auth.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.UnauthorizedException;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Authenticates browser cookies and Bearer JWTs against current account state. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Map<UUID, Long> ACTIVE_USERS = new ConcurrentHashMap<>();

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AccountAccessGuard accountAccessGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthFilter(
            JwtService jwtService,
            UserRepository userRepository,
            AccountAccessGuard accountAccessGuard) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.accountAccessGuard = accountAccessGuard;
    }

    public static int getActiveUsersCount() {
        long threshold = System.currentTimeMillis() - 5 * 60 * 1000;
        ACTIVE_USERS.values().removeIf(lastSeen -> lastSeen < threshold);
        return ACTIVE_USERS.size();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = bearerToken(request.getHeader("Authorization"));
        if (token == null) {
            token = cookieToken(request);
        }
        if (token != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && !authenticate(token, request, response)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean authenticate(
            String token,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            AuthenticatedUser tokenPrincipal = jwtService.parse(token);
            AccountBlockedException restriction = currentRestriction(tokenPrincipal.id());
            if (restriction != null && !isRestrictedAccountRoute(request)) {
                SecurityContextHolder.clearContext();
                writeRestrictedResponse(response, restriction);
                return false;
            }
            User user = userRepository.findById(tokenPrincipal.id())
                    .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
            setAuthentication(currentPrincipal(user), request);
            ACTIVE_USERS.put(user.getId(), System.currentTimeMillis());
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            return true;
        } catch (UnauthorizedException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    private AccountBlockedException currentRestriction(UUID userId) {
        try {
            accountAccessGuard.assertProductAccess(userId);
            return null;
        } catch (AccountBlockedException ex) {
            return ex;
        }
    }

    private AuthenticatedUser currentPrincipal(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }

    private void setAuthentication(AuthenticatedUser principal, HttpServletRequest request) {
        var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(authority));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
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

    private boolean isRestrictedAccountRoute(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && "/api/auth/logout".equals(path)) {
            return true;
        }
        if ("GET".equals(method)
                && ("/api/auth/me".equals(path) || "/api/account/session-state".equals(path))) {
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

    private void writeRestrictedResponse(
            HttpServletResponse response,
            AccountBlockedException restriction) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = ErrorResponse.builder()
                .code(restriction.getCode())
                .message(restriction.getSafeReason())
                .build();
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
    }
}
