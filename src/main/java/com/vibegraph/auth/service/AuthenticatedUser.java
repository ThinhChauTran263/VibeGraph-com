package com.vibegraph.auth.service;

import java.util.UUID;

import com.vibegraph.auth.domain.Role;

/**
 * Immutable principal parsed from a verified JWT and placed in the {@code SecurityContext}.
 * Carries the minimum needed downstream: the user id (subject), email, role, and optional session id.
 *
 * @param id    user id (JWT subject)
 * @param email user email (claim)
 * @param role      application role (claim)
 * @param sessionId refresh-session id (JWT {@code sid} claim), nullable for legacy tokens
 */
public record AuthenticatedUser(UUID id, String email, Role role, UUID sessionId) {

    /** Backward-compatible constructor for tokens created before session binding. */
    public AuthenticatedUser(UUID id, String email, Role role) {
        this(id, email, role, null);
    }
}
