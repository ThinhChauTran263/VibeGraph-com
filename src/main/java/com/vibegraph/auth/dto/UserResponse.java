package com.vibegraph.auth.dto;

import com.vibegraph.auth.domain.User;

/**
 * Non-sensitive user projection returned to clients. Deliberately excludes
 * {@code passwordHash} and all internal auth state.
 *
 * <p>Frontend contract: {@code { id, email, displayName, role }}. Used both as the {@code user}
 * field of {@link AuthResponse} and as the body of {@code GET /api/auth/me}.
 *
 * @param id          user id (UUID as string)
 * @param email       user email
 * @param displayName display name
 * @param role        application role (USER / ADMIN)
 */
public record UserResponse(String id, String email, String displayName, String role) {

    /** Map an entity to its safe projection. Never copies the password hash. */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId() != null ? user.getId().toString() : null,
                user.getEmail(),
                user.getDisplayName(),
                user.getRole() != null ? user.getRole().name() : null);
    }
}
