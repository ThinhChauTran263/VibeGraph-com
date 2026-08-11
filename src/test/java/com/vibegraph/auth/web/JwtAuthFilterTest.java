package com.vibegraph.auth.web;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.AccountAccessGuard.AccountAccessDecision;
import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.AccountDeactivatedException;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT auth filter")
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private AccountAccessGuard accountAccessGuard;

    private JwtAuthFilter filter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, accountAccessGuard);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Stub the single authentication read the filter now performs.
     *
     * <p>Identity, restriction and session liveness used to come from three different collaborators;
     * they arrive together, so the tests describe an outcome rather than a sequence of lookups.
     */
    private void whenAuthenticated(
            String token,
            UUID userId,
            String email,
            UUID sessionId,
            AccountBlockedException restriction,
            boolean sessionUsable) {
        when(jwtService.parse(token))
                .thenReturn(new AuthenticatedUser(userId, email, Role.USER, sessionId));
        when(accountAccessGuard.authenticate(userId, sessionId)).thenReturn(new AccountAccessDecision(
                new AuthenticatedUser(userId, email, Role.USER, sessionId), restriction, sessionUsable));
    }

    @Test
    @DisplayName("valid JWT for active account authenticates and continues")
    void doFilterInternal_activeUser_authenticatesAndContinues() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("valid-token", userId, "active@test.local", null, null, true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
        verify(accountAccessGuard).authenticate(userId, null);
    }

    @Test
    @DisplayName("valid JWT from HttpOnly session cookie authenticates browser requests")
    void doFilterInternal_cookieToken_authenticatesAndContinues() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieService.COOKIE_NAME, "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("cookie-token", userId, "cookie@test.local", null, null, true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
        verify(accountAccessGuard).authenticate(userId, null);
    }

    @Test
    @DisplayName("revoked refresh session invalidates its access JWT")
    void doFilterInternal_revokedSession_returnsUnauthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("revoked-session-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("revoked-session-token", userId, "revoked@test.local", sessionId, null, false);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("revoked refresh session also invalidates access to session-state routes")
    void doFilterInternal_revokedSessionOnMeRoute_returnsUnauthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("revoked-me-token");
        request.setMethod("GET");
        request.setRequestURI("/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("revoked-me-token", userId, "revoked@test.local", sessionId, null, false);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("a token issued before sessions existed is still accepted")
    void doFilterInternal_legacyTokenWithoutSessionId_authenticates() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("legacy-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        // No sid claim: the guard reports the session as usable so the token keeps working until it
        // expires on its own.
        whenAuthenticated("legacy-token", userId, "legacy@test.local", null, null, true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("valid JWT for blocked account returns ACCOUNT_BLOCKED and stops chain")
    void doFilterInternal_blockedUser_returnsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("blocked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("blocked-token", userId, "blocked@test.local", null,
                new AccountBlockedException("Account is blocked", "policy violation"), true);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("ACCOUNT_BLOCKED"));
        assertTrue(response.getContentAsString().contains("policy violation"));
    }

    @Test
    @DisplayName("valid JWT for deactivated account returns structured ACCOUNT_DEACTIVATED on product routes")
    void doFilterInternal_deactivatedUser_returnsStructuredRestriction() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("deactivated-token");
        request.setMethod("POST");
        request.setRequestURI("/api/projects/import-local");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("deactivated-token", userId, "deactivated@test.local", null,
                new AccountDeactivatedException(
                        "internal deactivation reason", "Account closed by administrator"),
                true);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCOUNT_DEACTIVATED"));
        assertTrue(response.getContentAsString().contains("Account closed by administrator"));
        assertFalse(response.getContentAsString().contains("private note"));
    }

    @Test
    @DisplayName("blocked account can access session state for safe polling")
    void doFilterInternal_blockedUser_allowsSessionState() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("blocked-token");
        request.setMethod("GET");
        request.setRequestURI("/api/account/session-state");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("blocked-token", userId, "blocked@test.local", null,
                new AccountBlockedException("internal reason", "Policy review"), true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertFalse(response.getContentAsString().contains("internal reason"));
    }

    @Test
    @DisplayName("deactivated account can access session state for safe polling")
    void doFilterInternal_deactivatedUser_allowsSessionState() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("deactivated-token");
        request.setMethod("GET");
        request.setRequestURI("/api/account/session-state");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("deactivated-token", userId, "deactivated@test.local", null,
                new AccountDeactivatedException(
                        "internal admin note", "Account closed by administrator"),
                true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertFalse(response.getContentAsString().contains("internal admin note"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("productRoutes")
    @DisplayName("blocked account is rejected on product routes")
    void doFilterInternal_blockedUser_rejectsProductRoutes(String method, String path) throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("blocked-token");
        request.setMethod(method);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("blocked-token", userId, "blocked@test.local", null,
                new AccountBlockedException("internal block reason", "Policy review"), true);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCOUNT_BLOCKED"));
        assertTrue(response.getContentAsString().contains("Policy review"));
        assertFalse(response.getContentAsString().contains("internal block reason"));
    }

    @Test
    @DisplayName("restricted accounts can access only their feedback report routes")
    void doFilterInternal_blockedUser_allowsReports() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("blocked-token");
        request.setMethod("POST");
        request.setRequestURI("/api/account/reports/" + UUID.randomUUID() + "/messages");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("blocked-token", userId, "blocked@test.local", null,
                new AccountBlockedException("Account is blocked", "Policy review"), true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("restricted accounts cannot use report-like near-match routes")
    void doFilterInternal_blockedUser_rejectsNearMatchPath() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("blocked-token");
        request.setMethod("GET");
        request.setRequestURI("/api/account/reports-extra");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        whenAuthenticated("blocked-token", userId, "blocked@test.local", null,
                new AccountBlockedException("Account is blocked", "Policy review"), true);

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("invalid JWT remains unauthenticated and continues")
    void doFilterInternal_invalidToken_continuesUnauthenticated() throws Exception {
        MockHttpServletRequest request = requestWithToken("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("invalid-token")).thenThrow(new JwtException("bad token"));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
        verifyNoInteractions(accountAccessGuard);
    }

    @Test
    @DisplayName("an account deleted after its token was issued is rejected with 401")
    void doFilterInternal_missingAccount_returnsUnauthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = requestWithToken("orphan-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("orphan-token"))
                .thenReturn(new AuthenticatedUser(userId, "gone@test.local", Role.USER, null));
        when(accountAccessGuard.authenticate(userId, null))
                .thenThrow(new com.vibegraph.common.exception.UnauthorizedException(
                        "Authenticated user not found"));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
    }

    private static Stream<Arguments> productRoutes() {
        return Stream.of(
                Arguments.of("POST", "/api/projects/import-local"),
                Arguments.of("POST", "/api/projects/import-archive"),
                Arguments.of("POST", "/api/projects/import-github"),
                Arguments.of("POST", "/api/projects/p1/patch"),
                Arguments.of("POST", "/api/projects/p1/analyze"),
                Arguments.of("POST", "/api/account/api-keys"),
                Arguments.of("POST", "/mcp/message"));
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
