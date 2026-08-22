package com.vibegraph.mcp.orchestration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Runtime guardrails for bounded, terminal task retention. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "vibegraph.mcp.orchestration.retention")
public class AgentTaskRetentionProperties {

    /** Enabled by default because every tool call creates a task row. */
    private boolean enabled = true;

    @Min(1)
    @Max(3650)
    private int retentionDays = 90;

    @Min(1)
    @Max(10_000)
    private int batchSize = 500;

    @Min(1)
    @Max(1_000)
    private int maxBatches = 20;

    @NotBlank
    private String cron = "0 15 3 * * ?";
}
