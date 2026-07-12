package com.vibegraph.auth.web;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.common.exception.AccountBlockedException;

import io.jsonwebtoken.JwtException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT auth filter")
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private AccountSettingsService accountSettingsService;

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("valid JWT for active account authenticates and continues")
    void doFilterInternal_activeUser_authenticatesAndContinues() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("valid-token"))
                .thenReturn(new AuthenticatedUser(userId, "active@test.local", Role.USER));
        when(userRepository.findById(userId))
                .thenReturn(java.util.Optional.of(User.builder().id(userId).deactivated(false).build()));

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
        verify(accountSettingsService).assertNotBlocked(userId);
    }

    @Test
    @DisplayName("valid JWT for blocked account returns ACCOUNT_BLOCKED and stops chain")
    void doFilterInternal_blockedUser_returnsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("blocked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("blocked-token"))
                .thenReturn(new AuthenticatedUser(userId, "blocked@test.local", Role.USER));
        doThrow(new AccountBlockedException("Account is blocked", "policy violation"))
                .when(accountSettingsService).assertNotBlocked(userId);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("ACCOUNT_BLOCKED"));
        assertTrue(response.getContentAsString().contains("policy violation"));
    }

    @Test
    @DisplayName("valid JWT for deactivated account returns 401 and stops chain")
    void doFilterInternal_deactivatedUser_returnsUnauthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("deactivated-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("deactivated-token"))
                .thenReturn(new AuthenticatedUser(userId, "deactivated@test.local", Role.USER));
        when(userRepository.findById(userId))
                .thenReturn(java.util.Optional.of(User.builder().id(userId).deactivated(true).build()));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("invalid JWT remains unauthenticated and continues")
    void doFilterInternal_invalidToken_continuesUnauthenticated() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("invalid-token")).thenThrow(new JwtException("bad token"));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
        verifyNoInteractions(accountSettingsService);
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
