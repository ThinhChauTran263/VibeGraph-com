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
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;
import com.vibegraph.common.exception.AccountBlockedException;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Stateless bearer-token filter. Reads {@code Authorization: Bearer <jwt>}, verifies it, and
 * populates the {@link SecurityContextHolder} with an {@link AuthenticatedUser} principal and a
 * {@code ROLE_*} authority.
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
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                AuthenticatedUser principal = jwtService.parse(token);
                accountSettingsService.assertNotBlocked(principal.id());
                
                var userOpt = userRepository.findById(principal.id());
                if (userOpt.isEmpty() || userOpt.get().isDeactivated()) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(authority));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                activeUsers.put(principal.id(), System.currentTimeMillis());
            } catch (AccountBlockedException ex) {
                SecurityContextHolder.clearContext();
                writeBlockedResponse(response, ex);
                return;
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid/expired/malformed token — stay unauthenticated; entry point returns 401.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeBlockedResponse(HttpServletResponse response, AccountBlockedException ex)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getCode())
                .message(ex.getSafeReason())
                .build();
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
    }
}
