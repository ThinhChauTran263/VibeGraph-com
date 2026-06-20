package com.vibegraph.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Properties for local-path project handling ({@code vibegraph.projects.*}).
 *
 * <p>{@code allowedRoot} is the optional security boundary for user-supplied directory paths:
 * when set, both the local import and the server-side directory browser are confined to it.
 * It mirrors the {@code @Value("${vibegraph.projects.allowed-root:}")} already read by
 * {@code ProjectServiceImpl}; this class exposes the same value to the new local-import and
 * directory-browse code without duplicating the literal property key.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.projects")
public class ProjectsProperties {

    /** When set, user-supplied project paths and directory browsing must resolve inside this directory. */
    private String allowedRoot = "";
}
