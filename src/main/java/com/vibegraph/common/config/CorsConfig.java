package com.vibegraph.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * CORS startup validation.
 *
 * <p>Allowed origins come from {@code vibegraph.cors.allowed-origins}
 * (see {@link CorsProperties}), not hardcoded values — dev defaults to the Vue dev
 * server, prod is driven by the {@code CORS_ALLOWED_ORIGINS} env var.
 *
 * <p>B-L5: the actual CORS mapping lives in exactly ONE place —
 * {@code SecurityConfig.corsConfigurationSource()} — so the two registrations can
 * never drift apart. This class keeps only the fail-fast guard:
 *
 * <p>Because credentialed requests are enabled ({@code allowCredentials(true)}), a
 * wildcard origin {@code "*"} is rejected at startup: the CORS spec forbids
 * credentials with a wildcard, and Spring would otherwise fail opaquely at request
 * time. Failing fast here surfaces a misconfiguration immediately.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    public CorsConfig(CorsProperties properties) {
        if (properties.getAllowedOrigins().contains("*")) {
            throw new IllegalStateException(
                    "vibegraph.cors.allowed-origins must not contain \"*\" because "
                            + "allowCredentials(true) is enabled. List explicit origins instead.");
        }
    }
}
