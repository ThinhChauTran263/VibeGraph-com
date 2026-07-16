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
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.common.exception.AccountBlockedException;

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
    @DisplayName("valid JWT from HttpOnly session cookie authenticates browser requests")
    void doFilterInternal_cookieToken_authenticatesAndContinues() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieService.COOKIE_NAME, "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("cookie-token"))
                .thenReturn(new AuthenticatedUser(userId, "cookie@test.local", Role.USER));
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
    @DisplayName("valid JWT for deactivated account returns structured ACCOUNT_BLOCKED on product routes")
    void doFilterInternal_deactivatedUser_returnsStructuredRestriction() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("deactivated-token");
        request.setMethod("POST");
        request.setRequestURI("/api/projects/import-local");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("deactivated-token"))
                .thenReturn(new AuthenticatedUser(userId, "deactivated@test.local", Role.USER));
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(User.builder()
                .id(userId)
                .deactivated(true)
                .deactivationReason("private note")
                .deactivationReasonSafe("Account closed by administrator")
                .build()));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCOUNT_BLOCKED"));
        assertTrue(response.getContentAsString().contains("Account closed by administrator"));
        assertFalse(response.getContentAsString().contains("private note"));
    }

    @Test
    @DisplayName("blocked account can access session state for safe polling")
    void doFilterInternal_blockedUser_allowsSessionState() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("blocked-token");
        request.setMethod("GET");
        request.setRequestURI("/api/account/session-state");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("blocked-token"))
                .thenReturn(new AuthenticatedUser(userId, "blocked@test.local", Role.USER));
        when(userRepository.findById(userId))
                .thenReturn(java.util.Optional.of(User.builder().id(userId).deactivated(false).build()));
        doThrow(new AccountBlockedException("internal reason", "Policy review"))
                .when(accountSettingsService).assertNotBlocked(userId);

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
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("deactivated-token");
        request.setMethod("GET");
        request.setRequestURI("/api/account/session-state");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("deactivated-token"))
                .thenReturn(new AuthenticatedUser(userId, "deactivated@test.local", Role.USER));
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(User.builder()
                .id(userId)
                .deactivated(true)
                .deactivationReason("internal admin note")
                .deactivationReasonSafe("Account closed by administrator")
                .build()));

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
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("blocked-token");
        request.setMethod(method);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("blocked-token"))
                .thenReturn(new AuthenticatedUser(userId, "blocked@test.local", Role.USER));
        doThrow(new AccountBlockedException("internal block reason", "Policy review"))
                .when(accountSettingsService).assertNotBlocked(userId);

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
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("blocked-token");
        request.setMethod("POST");
        request.setRequestURI("/api/account/reports/" + UUID.randomUUID() + "/messages");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("blocked-token"))
                .thenReturn(new AuthenticatedUser(userId, "blocked@test.local", Role.USER));
        when(userRepository.findById(userId))
                .thenReturn(java.util.Optional.of(User.builder().id(userId).deactivated(false).build()));
        doThrow(new AccountBlockedException("Account is blocked", "Policy review"))
                .when(accountSettingsService).assertNotBlocked(userId);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("restricted accounts cannot use report-like near-match routes")
    void doFilterInternal_blockedUser_rejectsNearMatchPath() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, accountSettingsService, userRepository);
        MockHttpServletRequest request = requestWithToken("blocked-token");
        request.setMethod("GET");
        request.setRequestURI("/api/account/reports-extra");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.parse("blocked-token"))
                .thenReturn(new AuthenticatedUser(userId, "blocked@test.local", Role.USER));
        doThrow(new AccountBlockedException("Account is blocked", "Policy review"))
                .when(accountSettingsService).assertNotBlocked(userId);

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
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
