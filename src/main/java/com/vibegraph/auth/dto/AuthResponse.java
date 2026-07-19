package com.vibegraph.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Authentication result. This is the ONLY response shape permitted to carry a JWT.
 *
 * <p>Frontend contract: {@code { token, user: { id, email, displayName, role } }} (wrapped by
 * the standard {@code ApiResponse.data}).
 *
 * @param token signed JWT
 * @param user  non-sensitive user projection
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String token, UserResponse user) {
}
