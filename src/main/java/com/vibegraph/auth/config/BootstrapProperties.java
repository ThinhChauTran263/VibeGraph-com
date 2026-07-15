package com.vibegraph.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code vibegraph.auth.bootstrap.*} — the explicit, opt-in admin bootstrap.
 *
 * <p>{@code enabled} defaults to {@code false}: normal application startup NEVER creates an admin.
 * The bootstrap runs only when {@code enabled=true} is set deliberately (e.g. a one-shot admin
 * migration invocation). Provide {@code adminPassword} OR {@code adminPasswordHash} (a pre-computed
 * BCrypt hash); the runner fails fast if enabled without the required credentials.
 */
@ConfigurationProperties(prefix = "vibegraph.auth.bootstrap")
@Getter
@Setter
public class BootstrapProperties {

    /** Opt-in switch. Default false = no admin creation on normal startup. */
    private boolean enabled = false;

    /** Admin account email. Required when {@code enabled}. */
    private String adminEmail;

    /** Raw admin password (BCrypt-hashed by the runner). Use this OR {@code adminPasswordHash}. */
    private String adminPassword;

    /** Pre-computed BCrypt hash for the admin password. Use this OR {@code adminPassword}. */
    private String adminPasswordHash;
}
