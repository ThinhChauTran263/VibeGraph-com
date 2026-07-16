package com.vibegraph.auth.domain;

/**
 * Ownership-plane status stored in {@code projects.status}
 * (VARCHAR, CHECK IN ('ANALYZING','ANALYZED','FAILED')). Persisted by name.
 *
 * <p>This mirrors the user-visible lifecycle in the control plane; the data-plane
 * analysis detail still lives in Neo4j / the in-memory registry.
 */
public enum ProjectOwnershipStatus {
    ANALYZING,
    ANALYZED,
    FAILED
}
