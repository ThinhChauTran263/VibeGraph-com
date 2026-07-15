package com.vibegraph.auth.domain;

/**
 * Origin of a project, stored in {@code projects.source_type}
 * (VARCHAR, CHECK IN ('LOCAL','ARCHIVE','GITHUB')). Persisted by name.
 */
public enum ProjectSourceType {
    LOCAL,
    ARCHIVE,
    GITHUB
}
