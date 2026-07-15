package com.vibegraph.auth.dto;

/** Safe account/session state projection for clients polling current access status. */
public record AccountSessionStateResponse(
        String id,
        String email,
        String displayName,
        String role,
        String accountStatus,
        String safeReason) {

    public static AccountSessionStateResponse from(UserResponse user) {
        return new AccountSessionStateResponse(
                user.id(),
                user.email(),
                user.displayName(),
                user.role(),
                user.accountStatus(),
                user.safeReason());
    }
}
