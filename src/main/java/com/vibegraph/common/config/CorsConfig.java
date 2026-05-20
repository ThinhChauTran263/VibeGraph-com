package com.vibegraph.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration.
 * Allows Vue dev server (default: http://localhost:5173) to call Spring Boot APIs.
 *
 * TODO:
 * - Read allowed origins from application.yaml
 * - Tighten origins for production
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // TODO: Configure CORS
        // registry.addMapping("/api/**")
        //     .allowedOrigins("http://localhost:5173")
        //     .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        //     .allowedHeaders("*")
        //     .allowCredentials(true);
    }
}
