package com.vibegraph.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API-key gate for filesystem-touching endpoints.
 *
 * <p>When {@link #key} is blank (the default in dev/test), the gate is disabled and all requests
 * pass — local development and the test suite need no key. Set {@code vibegraph.api-key}
 * (env {@code VIBEGRAPH_API_KEY}) to a shared secret to require the {@code X-API-Key} header on the
 * project import / directory-browse endpoints, so an unauthenticated network client cannot drive
 * the server's filesystem access.
 *
 * <p>This is a lightweight shared-secret gate for internal/team use, not per-user authentication.
 * Because the SPA must send the key, it cannot be kept secret from someone who can already open the
 * web app; it raises the bar against opportunistic network access, nothing more.
 */
@ConfigurationProperties(prefix = "vibegraph")
public class ApiKeyProperties {

    /** Shared secret required in the {@code X-API-Key} header; blank disables the gate. */
    private String apiKey = "";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }
}
