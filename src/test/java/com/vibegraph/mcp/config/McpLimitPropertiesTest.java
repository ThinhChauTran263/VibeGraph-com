package com.vibegraph.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class McpLimitPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EnableConfig.class);

    @EnableConfigurationProperties(McpLimitProperties.class)
    static class EnableConfig {
    }

    @Test
    void defaults_use_approved_mcp_capacity() {
        McpLimitProperties properties = new McpLimitProperties();

        assertThat(properties.getMaxNodes()).isEqualTo(100_000);
        assertThat(properties.getMaxEdges()).isEqualTo(200_000);
    }

    @Test
    void setters_reject_non_positive_limits() {
        McpLimitProperties properties = new McpLimitProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxNodes(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxNodes(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxEdges(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxEdges(-1));
    }

    @Test
    void binds_environment_style_properties() {
        contextRunner.withPropertyValues(
                "vibegraph.mcp.max-nodes=120000",
                "vibegraph.mcp.max-edges=240000")
                .run(context -> {
                    McpLimitProperties properties = context.getBean(McpLimitProperties.class);

                    assertThat(properties.getMaxNodes()).isEqualTo(120_000);
                    assertThat(properties.getMaxEdges()).isEqualTo(240_000);
                });
    }

    @Test
    void rejects_invalid_bound_during_configuration_binding() {
        contextRunner.withPropertyValues("vibegraph.mcp.max-nodes=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
