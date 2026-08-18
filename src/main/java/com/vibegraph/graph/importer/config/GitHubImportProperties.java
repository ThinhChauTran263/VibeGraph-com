package com.vibegraph.graph.importer.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * GitHub import HTTP timeouts (read from application.yaml) for the GitHub onboarding flow
 * (pre-flight metadata check + tarball download).
 *
 * Example yaml:
 *
 * vibegraph:
 *   import:
 *     github:
 *       connect-timeout: 15s
 *       preflight-request-timeout: 20s
 *       tarball-request-timeout: 60s
 *       max-attempts: 3
 *       retry-initial-delay: 1s
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.import.github")
public class GitHubImportProperties {

    /** TCP connect timeout for both the pre-flight check and the tarball download. */
    private Duration connectTimeout = Duration.ofSeconds(15);

    /** Per-request timeout for the cheap repository metadata pre-flight call. */
    private Duration preflightRequestTimeout = Duration.ofSeconds(20);

    /** Per-request timeout for the (potentially large) tarball download. */
    private Duration tarballRequestTimeout = Duration.ofSeconds(60);

    /** Total tarball download attempts, including the initial request. */
    private int maxAttempts = 3;

    /** Initial retry delay; each subsequent retry doubles this value. */
    private Duration retryInitialDelay = Duration.ofSeconds(1);
}
