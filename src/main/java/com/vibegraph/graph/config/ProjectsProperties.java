package com.vibegraph.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Properties for local-path project handling ({@code vibegraph.projects.*}).
 *
 * <p>{@code allowedRoot} is the optional security boundary for user-supplied directory paths:
 * project {@code rootPath} values must resolve inside it when set. It mirrors the
 * {@code @Value("${vibegraph.projects.allowed-root:}")} already read by {@code ProjectServiceImpl};
 * this class exposes the same value to the path-validation code without duplicating the
 * literal property key.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.projects")
public class ProjectsProperties {

    /** When set, user-supplied project paths must resolve inside this directory. */
    private String allowedRoot = "";

    /** Explicit development/test opt-in for importing outside a configured root. */
    private boolean allowUnconfinedImport = false;

    /**
     * How long a deleted project stays in trash before it is permanently removed.
     *
     * <p>Until the window expires the project is hidden but fully restorable, and it keeps counting
     * toward the owner's quota because its graph and extracted sources still occupy storage.
     *
     * <p>The sweep that enforces this window runs on {@code vibegraph.projects.trash-sweep-cron}
     * (default {@code 0 30 3 * * ?}), read directly by {@code ProjectTrashService}.
     */
    @Min(1)
    private int trashRetentionDays = 3;
}
