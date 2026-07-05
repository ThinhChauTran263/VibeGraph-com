package com.vibegraph.auth.domain;

/**
 * External identity provider stored in {@code user_identities.provider}
 * (VARCHAR, CHECK IN ('GOOGLE','GITHUB')). Persisted by name.
 *
 * <p>Phase 1 creates the table but leaves OAuth wiring deferred; this enum exists so the
 * schema is frozen and the future OAuth card needs no migration.
 */
public enum AuthProvider {
    GOOGLE,
    GITHUB
}
