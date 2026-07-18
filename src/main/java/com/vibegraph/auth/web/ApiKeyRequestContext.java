package com.vibegraph.auth.web;

/** Safe request-scoped API-key identity and project binding. */
public record ApiKeyRequestContext(String keyRef, String projectId) {
}
