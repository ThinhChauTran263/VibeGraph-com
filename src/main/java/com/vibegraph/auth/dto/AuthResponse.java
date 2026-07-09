package com.vibegraph.auth.dto;

/**
 * Authentication result. This is the ONLY response shape permitted to carry a JWT.
 *
 * <p>Frontend contract: {@code { token, user: { id, email, displayName, role } }} (wrapped by
 * the standard {@code ApiResponse.data}).
 *
 * @param token signed JWT
 * @param user  non-sensitive user projection
 */
public record AuthResponse(String token, UserResponse user) {
}
