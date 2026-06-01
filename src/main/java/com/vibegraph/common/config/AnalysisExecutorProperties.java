package com.vibegraph.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Bounded thread-pool settings for the async analysis/import executor (bean
 * {@code analysisExecutor}). Prefix {@code vibegraph.analysis.executor}.
 *
 * <p>Used by the async archive-import path; the default synchronous upload/analyze flow does not use it.
 */
@Data
@ConfigurationProperties(prefix = "vibegraph.analysis.executor")
public class AnalysisExecutorProperties {

    /** Threads kept alive even when idle. */
    private int corePoolSize = 2;

    /** Hard cap on concurrent analysis threads (parsing is CPU-bound). */
    private int maxPoolSize = 4;

    /** Tasks queued before the pool grows toward maxPoolSize / the rejection policy applies. */
    private int queueCapacity = 50;

    /** Prefix for worker thread names (eases log / thread-dump triage). */
    private String threadNamePrefix = "analysis-";
}
