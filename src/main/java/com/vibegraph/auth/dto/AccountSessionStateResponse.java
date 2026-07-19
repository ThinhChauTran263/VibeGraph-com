package com.vibegraph.auth.dto;

import java.util.Map;

/** Safe account/session state projection for clients polling current access status. */
public record AccountSessionStateResponse(
        String id,
        String email,
        String displayName,
        String role,
        String accountStatus,
        String safeReason,
        Map<String, FeatureCapability> features) {

    public AccountSessionStateResponse {
        features = features == null ? Map.of() : Map.copyOf(features);
    }

    public AccountSessionStateResponse(
            String id,
            String email,
            String displayName,
            String role,
            String accountStatus,
            String safeReason) {
        this(id, email, displayName, role, accountStatus, safeReason, Map.of());
    }

    public static AccountSessionStateResponse from(UserResponse user, Map<String, FeatureCapability> features) {
        return new AccountSessionStateResponse(
                user.id(),
                user.email(),
                user.displayName(),
                user.role(),
                user.accountStatus(),
                user.safeReason(),
                features);
    }

    public static AccountSessionStateResponse from(UserResponse user) {
        return from(user, Map.of());
    }
}
