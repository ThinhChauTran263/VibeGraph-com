package com.vibegraph.auth.web;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.vibegraph.auth.domain.ApiKey;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.AuthenticatedUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String API_KEY_REF_ATTRIBUTE = "vibegraph.apiKeyRef";

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final AccountSettingsService accountSettingsService;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository, UserRepository userRepository,
            AccountSettingsService accountSettingsService, PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.accountSettingsService = accountSettingsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(API_KEY_HEADER);
        if (presented == null || presented.isBlank()
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            findMatch(presented.trim()).ifPresent(key -> authenticate(request, key));
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
        Instant now = Instant.now();
        return apiKeyRepository.findAll().stream()
                .filter(key -> prefix.equals(key.getKeyPrefix()))
                .filter(key -> key.getDisabledAt() == null)
                .filter(key -> key.getExpiresAt() == null || key.getExpiresAt().isAfter(now))
                .filter(key -> passwordEncoder.matches(presented, key.getKeyHash()))
                .findFirst();
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

    private void authenticate(HttpServletRequest request, ApiKey key) {
        userRepository.findById(key.getUserId()).ifPresent(user -> {
            if (user.isDeactivated()) {
                return;
            }
            accountSettingsService.assertNotBlocked(user.getId());
            AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute(API_KEY_REF_ATTRIBUTE, key.getId() + ":" + key.getKeyPrefix());
            key.setLastUsedAt(Instant.now());
            apiKeyRepository.save(key);
        });
    }
}
