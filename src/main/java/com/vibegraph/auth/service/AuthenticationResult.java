package com.vibegraph.auth.service;

import com.vibegraph.auth.dto.AuthResponse;
import com.vibegraph.auth.dto.UserResponse;

/** Internal authentication result; the raw refresh token is never serialized or logged. */
public final class AuthenticationResult {

    private final String accessToken;
    private final String refreshToken;
    private final UserResponse user;

    public AuthenticationResult(String accessToken, String refreshToken, UserResponse user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public UserResponse user() {
        return user;
    }

    public AuthResponse response(boolean exposeAccessToken) {
        return new AuthResponse(exposeAccessToken ? accessToken : null, user);
    }

    @Override
    public String toString() {
        return "AuthenticationResult{userId=" + (user == null ? null : user.id()) + "}";
    }
}
