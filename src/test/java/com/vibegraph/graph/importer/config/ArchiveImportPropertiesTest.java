package com.vibegraph.graph.importer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

/**
 * Verifies ArchiveImportProperties binds from config with safe defaults and honors overrides.
 * Uses ApplicationContextRunner so no Neo4j/full app context is required.
 */
class ArchiveImportPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableConfig.class);

    @EnableConfigurationProperties(ArchiveImportProperties.class)
    static class EnableConfig {
    }

    @Test
    void bindsSafeDefaults() {
        runner.run(ctx -> {
            ArchiveImportProperties props = ctx.getBean(ArchiveImportProperties.class);
            assertThat(props.getMaxSize()).isEqualTo(DataSize.ofMegabytes(100));
            assertThat(props.getWorkspaceRoot().toString()).contains("vibegraph");
            assertThat(props.getIgnoredPaths())
                    .containsExactly("target", "build", ".git", ".idea", "node_modules");
        });
    }

    @Test
    void bindsOverridesFromConfig() {
        runner.withPropertyValues(
                "vibegraph.import.archive.max-size=10MB",
                "vibegraph.import.archive.workspace-root=/tmp/vg-uploads",
                "vibegraph.import.archive.ignored-paths=foo,bar"
        ).run(ctx -> {
            ArchiveImportProperties props = ctx.getBean(ArchiveImportProperties.class);
            assertThat(props.getMaxSize()).isEqualTo(DataSize.ofMegabytes(10));
            assertThat(props.getWorkspaceRoot()).isEqualTo(Paths.get("/tmp/vg-uploads"));
            assertThat(props.getIgnoredPaths()).containsExactly("foo", "bar");
        });
    }
}
