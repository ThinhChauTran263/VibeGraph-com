package com.vibegraph.auth.web;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AuthResponse;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.config.JwtProperties;
import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.auth.service.AuthService;
import com.vibegraph.auth.service.AuditService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AuthController")
class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setExpirationMs(86_400_000L);
        AuthCookieService cookieService = new AuthCookieService(jwtProperties);
        AuthController controller = new AuthController(authService, cookieService, auditService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("web login sets an HttpOnly JWT cookie and omits token from body")
    void login_webClient_setsHttpOnlyCookieAndHidesBodyToken() throws Exception {
        UserResponse user = new UserResponse(
                UUID.randomUUID().toString(),
                "user@test.local",
                "User",
                "USER",
                "ACTIVE",
                null);
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-token", user));

        mockMvc.perform(post("/api/auth/login")
                        .header("X-VibeGraph-Client", "web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.local\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("vg_session=jwt-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.user.email").value("user@test.local"));
    }

    @Test
    @DisplayName("CLI login keeps bearer token in body for backward compatibility")
    void login_cliClient_keepsBodyToken() throws Exception {
        UserResponse user = new UserResponse(
                UUID.randomUUID().toString(),
                "cli@test.local",
                "CLI",
                "USER",
                "ACTIVE",
                null);
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-token", user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cli@test.local\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("vg_session=jwt-token")))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.user.email").value("cli@test.local"));
    }

    @Test
    @DisplayName("logout clears the HttpOnly JWT cookie")
    void logout_clearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("vg_session=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("jwt-token"))));
    }
}
