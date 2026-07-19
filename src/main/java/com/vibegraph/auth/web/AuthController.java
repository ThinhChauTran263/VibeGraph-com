package com.vibegraph.auth.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.AuthResponse;
import com.vibegraph.auth.dto.LoginRequest;
import com.vibegraph.auth.dto.RegisterRequest;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.auth.service.AuthService;
import com.vibegraph.auth.service.AuditService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Local authentication endpoints (Google OAuth deferred).
 *
 * <ul>
 *   <li>{@code POST /api/auth/register} → sets a browser cookie and returns {@code { user }}</li>
 *   <li>{@code POST /api/auth/login} → sets a browser cookie and returns {@code { user }}</li>
 *   <li>{@code GET /api/auth/me} → {@code { id, email, displayName, role }}</li>
 * </ul>
 *
 * <p>{@code register}/{@code login} are on the security permit list; {@code me} requires a valid
 * cookie or Bearer token (401 otherwise). {@code @Valid} makes malformed input a 400 before the
 * service runs.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String WEB_CLIENT_HEADER = "X-VibeGraph-Client";
    private static final String WEB_CLIENT_VALUE = "web";

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final AuditService auditService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = WEB_CLIENT_HEADER, required = false) String client,
            HttpServletRequest servletRequest) {
        return authResponse(authService.register(request), client, servletRequest);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = WEB_CLIENT_HEADER, required = false) String client,
            HttpServletRequest servletRequest) {
        return authResponse(authService.login(request), client, servletRequest);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest servletRequest) {
        auditService.recordCurrentUser("LOGOUT", null, "SESSION", null, java.util.Map.of());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearCookie(servletRequest).toString())
                .body(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success(authService.currentUser()));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> authResponse(
            AuthResponse response,
            String client,
            HttpServletRequest servletRequest) {
        AuthResponse body = isWebClient(client)
                ? new AuthResponse(null, response.user())
                : response;
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        authCookieService.sessionCookie(response.token(), servletRequest).toString())
                .body(ApiResponse.success(body));
    }

    private boolean isWebClient(String client) {
        return WEB_CLIENT_VALUE.equalsIgnoreCase(client);
    }
}
