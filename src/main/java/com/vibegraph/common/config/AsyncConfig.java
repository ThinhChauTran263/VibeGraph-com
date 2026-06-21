package com.vibegraph.common.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async configuration. Provides a bounded {@code analysisExecutor} used by the async
 * archive-import path; the default synchronous upload/analyze flow does not use it.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AnalysisExecutorProperties.class)
public class AsyncConfig {

    /**
     * Bounded pool for CPU-bound analysis/parsing using platform threads (not virtual, to
     * avoid CPU oversubscription). When core threads, the bounded queue, and maxPoolSize are all
     * saturated, {@link ThreadPoolExecutor.AbortPolicy} rejects the task with a
     * {@code RejectedExecutionException} rather than running it on the submitting (Tomcat) thread.
     * The import services catch that, mark the project FAILED, and return 503 — this preserves the
     * non-blocking 202 contract instead of tying up the servlet thread for a full analysis.
     */
    @Bean("analysisExecutor")
    public ThreadPoolTaskExecutor analysisExecutor(AnalysisExecutorProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setThreadNamePrefix(props.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
