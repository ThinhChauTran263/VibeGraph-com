package com.vibegraph.auth.dto;

import com.vibegraph.auth.domain.entity.User;

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
public record UserResponse(
        String id,
        String email,
        String displayName,
        String role,
        String accountStatus,
        String safeReason) {

    private static final String DEFAULT_BLOCKED_REASON = "Account access is restricted";
    private static final String DEFAULT_DEACTIVATED_REASON = "Account closed by administrator";

    /** Map an entity and account settings to a safe projection. */
    public static UserResponse from(User user, com.vibegraph.auth.domain.entity.UserAccountSettings settings) {
        boolean isBlocked = settings != null && settings.isBlocked();
        String accountStatus = isBlocked ? "BLOCKED" : user.isDeactivated() ? "DEACTIVATED" : "ACTIVE";
        String safeReason = isBlocked
                ? safe(settings.getBlockedReasonSafe(), DEFAULT_BLOCKED_REASON)
                : user.isDeactivated()
                        ? safe(user.getDeactivationReasonSafe(), DEFAULT_DEACTIVATED_REASON)
                        : null;
        return new UserResponse(
                user.getId() != null ? user.getId().toString() : null,
                user.getEmail(),
                user.getDisplayName(),
                user.getRole() != null ? user.getRole().name() : null,
                accountStatus,
                safeReason);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
