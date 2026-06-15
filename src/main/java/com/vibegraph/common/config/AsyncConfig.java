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
     * avoid CPU oversubscription). When core threads, the queue, and maxPoolSize are all
     * saturated, {@link ThreadPoolExecutor.CallerRunsPolicy} applies backpressure by running
     * the task on the submitting thread - tasks are never dropped.
     */
    @Bean("analysisExecutor")
    public ThreadPoolTaskExecutor analysisExecutor(AnalysisExecutorProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setThreadNamePrefix(props.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
