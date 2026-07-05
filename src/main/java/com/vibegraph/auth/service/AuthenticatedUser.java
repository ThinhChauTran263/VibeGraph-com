package com.vibegraph.auth.service;

import java.util.UUID;

import com.vibegraph.auth.domain.Role;

/**
 * Immutable principal parsed from a verified JWT and placed in the {@code SecurityContext}.
 * Carries the minimum needed downstream: the user id (subject), email, and role.
 *
 * @param id    user id (JWT subject)
 * @param email user email (claim)
 * @param role  application role (claim)
 */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
