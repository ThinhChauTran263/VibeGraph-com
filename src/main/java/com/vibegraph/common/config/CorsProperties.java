package com.vibegraph.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS settings bound from {@code vibegraph.cors.*}.
 *
 * <p>Binds a YAML sequence (dev) or a comma-separated / single scalar from an env var
 * (prod, via {@code ${CORS_ALLOWED_ORIGINS}}) into {@link #allowedOrigins}. The default
 * targets the local Vue dev server and the Docker frontend so a missing config still
 * works for local development.
 */
@ConfigurationProperties(prefix = "vibegraph.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of(
            "http://localhost:5173",
            "http://localhost:3000");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
