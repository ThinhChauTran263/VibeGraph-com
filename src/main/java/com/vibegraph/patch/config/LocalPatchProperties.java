package com.vibegraph.patch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Server-side guardrails for the Local Patch endpoint
 * ({@code POST /api/projects/{projectId}/patch}).
 *
 * <p>The CLI streams local file changes into an imported project's on-disk root. These caps bound
 * the blast radius of a single patch request so a client cannot exhaust disk or memory:
 * <ul>
 *   <li>{@link #maxFiles} — max entries allowed in either {@code files} or {@code deletions};</li>
 *   <li>{@link #maxFileBytes} — max decoded size of any single changed file;</li>
 *   <li>{@link #maxTotalBytes} — max cumulative decoded size across all changed files.</li>
 * </ul>
 *
 * <p>Defaults are intentionally conservative (200 files / 1 MB per file / 5 MB total) and can be
 * overridden under {@code vibegraph.patch.*} in {@code application.yaml}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.patch")
public class LocalPatchProperties {

    /** Maximum number of changed files (and, independently, deletions) per request. */
    private int maxFiles = 200;

    /** Maximum decoded size, in bytes, of a single changed file. */
    private long maxFileBytes = 1L * 1024 * 1024;

    /** Maximum cumulative decoded size, in bytes, across all changed files in one request. */
    private long maxTotalBytes = 5L * 1024 * 1024;
}
