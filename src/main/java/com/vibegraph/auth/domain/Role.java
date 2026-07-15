package com.vibegraph.auth.domain;

/**
 * Application role stored in {@code users.role} (VARCHAR, CHECK IN ('USER','ADMIN')).
 * Persisted by name via {@code @Enumerated(EnumType.STRING)}.
 */
public enum Role {
    USER,
    ADMIN
}
