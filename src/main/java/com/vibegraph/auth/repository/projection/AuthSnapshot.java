package com.vibegraph.auth.repository.projection;

import java.util.UUID;

import com.vibegraph.auth.domain.Role;

/**
 * Everything {@code JwtAuthFilter} needs about a caller, read in a single round trip.
 *
 * <p>The filter used to ask the database four separate questions per authenticated request: load
 * the user, load its account settings, check the refresh session, then load the same user again.
 * With {@code open-in-view: false} each of those ran in its own transaction, so none of them shared
 * a persistence context and every one was a real round trip. This projection collapses them into
 * one query, which costs little against a local database but decides the request latency once
 * Postgres is remote.
 *
 * @param sessionActive whether the refresh session named by the JWT {@code sid} is still live;
 *                      {@code false} when the token carries no {@code sid}, which the caller
 *                      interprets separately for backwards compatibility
 */
public record AuthSnapshot(
        UUID id,
        String email,
        Role role,
        boolean deactivated,
        String deactivationReasonSafe,
        boolean blocked,
        String blockedReasonSafe,
        boolean sessionActive) {
}
