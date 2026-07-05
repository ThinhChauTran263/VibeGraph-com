package com.vibegraph.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code vibegraph.auth.realtime.*} — the interim access policy for the realtime
 * ({@code /ws/**}) and MCP ({@code /mcp/**}) endpoints in Phase 1.
 *
 * <p>{@code demoPermit} defaults to {@code false} = <b>fail closed</b>: those routes require
 * authentication like everything else. Setting it {@code true} permits them for demo/local use
 * only; {@link SecurityConfig} logs a prominent startup WARNING and the routes are explicitly
 * documented as NOT multi-user safe (full per-connection auth is Phase 3).
 */
@ConfigurationProperties(prefix = "vibegraph.auth.realtime")
@Getter
@Setter
public class RealtimeSecurityProperties {

    /** When true, permit /ws/** and /mcp/** without auth (demo/local only). Default false. */
    private boolean demoPermit = false;
}
