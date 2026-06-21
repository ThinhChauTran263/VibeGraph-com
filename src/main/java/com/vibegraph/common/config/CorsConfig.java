package com.vibegraph.common.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration.
 *
 * <p>Allowed origins come from {@code vibegraph.cors.allowed-origins}
 * (see {@link CorsProperties}), not hardcoded values — dev defaults to the Vue dev
 * server, prod is driven by the {@code CORS_ALLOWED_ORIGINS} env var.
 *
 * <p>Because credentialed requests are enabled ({@code allowCredentials(true)}), a
 * wildcard origin {@code "*"} is rejected at startup: the CORS spec forbids
 * credentials with a wildcard, and Spring would otherwise fail opaquely at request
 * time. Failing fast here surfaces a misconfiguration immediately.
 */
@Configuration
@EnableConfigurationProperties({CorsProperties.class, ApiKeyProperties.class})
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(CorsProperties properties) {
        this.allowedOrigins = properties.getAllowedOrigins();
        if (this.allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "vibegraph.cors.allowed-origins must not contain \"*\" because "
                            + "allowCredentials(true) is enabled. List explicit origins instead.");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
