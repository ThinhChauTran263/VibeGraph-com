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
import com.vibegraph.auth.service.AuthenticationResult;
import com.vibegraph.auth.service.AuthService;
import com.vibegraph.auth.service.AuditService;
import com.vibegraph.abuse.ClientAddressResolver;
import com.vibegraph.abuse.LoginThrottleGuard;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;
import com.vibegraph.common.exception.InvalidCredentialsException;
import com.vibegraph.common.exception.UnauthorizedException;

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
    private final LoginThrottleGuard loginThrottleGuard;
    private final ClientAddressResolver clientAddressResolver;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = WEB_CLIENT_HEADER, required = false) String client,
            HttpServletRequest servletRequest) {
        return authResponse(authService.registerSession(request), client, servletRequest);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = WEB_CLIENT_HEADER, required = false) String client,
            HttpServletRequest servletRequest) {
        // Resolved through ClientAddressResolver rather than getRemoteAddr(): it only honours
        // X-Forwarded-For from a configured trusted proxy, so the per-address budget cannot be
        // reset by spoofing a header.
        String clientIp = clientAddressResolver.resolve(servletRequest);
        // Checked before the password is verified, so a locked-out caller can neither keep guessing
        // nor time how long verification takes.
        loginThrottleGuard.assertAllowed(clientIp, request.email());
        try {
            ResponseEntity<ApiResponse<AuthResponse>> response =
                    authResponse(authService.loginSession(request), client, servletRequest);
            loginThrottleGuard.recordSuccess(clientIp, request.email());
            return response;
        } catch (InvalidCredentialsException ex) {
            // Only bad credentials consume budget. A blocked or deactivated account throws
            // something else and must not count as a guess, or an administrator action would
            // lock the account out on top of its existing restriction.
            loginThrottleGuard.recordFailure(clientIp, request.email());
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestHeader(value = WEB_CLIENT_HEADER, required = false) String client,
            HttpServletRequest servletRequest) {
        try {
            return authResponse(
                    authService.refreshSession(authCookieService.refreshToken(servletRequest)),
                    client,
                    servletRequest);
        } catch (UnauthorizedException ex) {
            return ResponseEntity.status(401)
                    .headers(clearAuthCookies(servletRequest))
                    .body(ApiResponse.<AuthResponse>error(ErrorResponse.builder()
                            .code("UNAUTHORIZED")
                            .message("Invalid refresh token")
                            .build()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest servletRequest) {
        authService.revokeRefreshSession(authCookieService.refreshToken(servletRequest));
        auditService.recordCurrentUser("LOGOUT", null, "SESSION", null, java.util.Map.of());
        return ResponseEntity.ok()
                .headers(clearAuthCookies(servletRequest))
                .body(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success(authService.currentUser()));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> authResponse(
            AuthenticationResult result,
            String client,
            HttpServletRequest servletRequest) {
        AuthResponse body = result.response(!isWebClient(client));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        authCookieService.sessionCookie(result.accessToken(), servletRequest).toString())
                .header(HttpHeaders.SET_COOKIE,
                        authCookieService.refreshCookie(result.refreshToken(), servletRequest).toString())
                .body(ApiResponse.success(body));
    }

    private org.springframework.http.HttpHeaders clearAuthCookies(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, authCookieService.clearCookie(request).toString());
        headers.add(HttpHeaders.SET_COOKIE, authCookieService.clearRefreshCookie(request).toString());
        return headers;
    }

    private boolean isWebClient(String client) {
        return WEB_CLIENT_VALUE.equalsIgnoreCase(client);
    }
}
