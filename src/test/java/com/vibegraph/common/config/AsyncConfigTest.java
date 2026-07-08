package com.vibegraph.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Verifies AsyncConfig builds a bounded {@code analysisExecutor} from
 * AnalysisExecutorProperties. Uses ApplicationContextRunner so no Neo4j/full app context.
 */
@DisplayName("AsyncConfig analysisExecutor")
class AsyncConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncConfig.class);

    @Test
    @DisplayName("creates a bounded executor with safe defaults")
    void createsBoundedExecutorWithDefaults() {
        runner.run(ctx -> {
            ThreadPoolTaskExecutor executor = ctx.getBean("analysisExecutor", ThreadPoolTaskExecutor.class);
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getQueueCapacity()).isEqualTo(50);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("analysis-");
        });
    }

    @Test
    @DisplayName("applies property overrides")
    void appliesPropertyOverrides() {
        runner.withPropertyValues(
                "vibegraph.analysis.executor.core-pool-size=3",
                "vibegraph.analysis.executor.max-pool-size=8",
                "vibegraph.analysis.executor.queue-capacity=100",
                "vibegraph.analysis.executor.thread-name-prefix=worker-"
        ).run(ctx -> {
            ThreadPoolTaskExecutor executor = ctx.getBean("analysisExecutor", ThreadPoolTaskExecutor.class);
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
            assertThat(executor.getMaxPoolSize()).isEqualTo(8);
            assertThat(executor.getQueueCapacity()).isEqualTo(100);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("worker-");
        });
    }
}
