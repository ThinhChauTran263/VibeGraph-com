package com.vibegraph.graph.dto.response;

/**
 * Project analysis lifecycle status. Stored on {@link ProjectResponse#getStatus()} as its
 * {@link #name()} wire value, kept stable for the API/frontend (e.g. {@code "CREATED"},
 * {@code "ANALYZED"}). Foundation for async import; no behavior change to existing endpoints.
 */
public enum ProjectStatus {
    CREATED,
    ANALYZING,
    ANALYZED,
    FAILED
}
