package com.vibegraph.auth.web;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.config.JwtProperties;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.auth.service.AuthenticationResult;
import com.vibegraph.abuse.AbuseProperties;
import com.vibegraph.abuse.ClientAddressResolver;
import com.vibegraph.abuse.LoginThrottleGuard;
import com.vibegraph.abuse.RegistrationThrottleGuard;
import com.vibegraph.auth.service.AuthService;
import com.vibegraph.auth.service.AuditService;
import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.UnauthorizedException;

import jakarta.servlet.http.Cookie;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthRefreshControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private AuthCookieService cookieService;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        JwtProperties properties = new JwtProperties();
        properties.setExpirationMs(1_800_000L);
        properties.setRefreshExpirationMs(604_800_000L);
        cookieService = new AuthCookieService(properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, cookieService, auditService,
                        new LoginThrottleGuard(new AbuseProperties(), java.time.Clock.systemUTC()),
                        new RegistrationThrottleGuard(new AbuseProperties(), java.time.Clock.systemUTC()),
                        new ClientAddressResolver(new AbuseProperties())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void refresh_rotatesCookiesAndHidesAccessTokenForWebClient() throws Exception {
        UserResponse user = new UserResponse(
                UUID.randomUUID().toString(), "user@test.local", "User", "USER", "ACTIVE", null);
        when(authService.refreshSession("refresh-token"))
                .thenReturn(new AuthenticationResult("access-token", "next-refresh", user));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-token"))
                        .header("X-VibeGraph-Client", "web"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("vg_session=access-token")))
                .andExpect(header().stringValues("Set-Cookie",
                        hasItems(containsString("vg_session=access-token"),
                                containsString("vg_refresh=next-refresh"))))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void refresh_rejectedToken_clearsBothCookies() throws Exception {
        when(authService.refreshSession(anyString())).thenThrow(new UnauthorizedException("Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "replayed"))
                        .header("X-VibeGraph-Client", "web"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().stringValues("Set-Cookie",
                        hasItems(containsString("vg_session="), containsString("vg_refresh="))))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void logout_revokesRefreshFamilyAndClearsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-token"))
                        .header("X-VibeGraph-Client", "web"))
                .andExpect(status().isOk());

        verify(authService).revokeRefreshSession(eq("refresh-token"));
    }
}
