package com.vibegraph.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "vibegraph.infrastructure.monitor")
public class InfrastructureMonitorProperties {

    private boolean enabled = true;

    @Min(250)
    private long sampleIntervalMs = 1_000;

    @Min(10)
    @Max(3_600)
    private int liveSampleCapacity = 60;

    @Min(10)
    @Max(10_000)
    private int operationHistoryCapacity = 500;

    @Min(1)
    @Max(200)
    private int operationHistoryInSnapshot = 20;

    @Min(0)
    private long operationCooldownMs = 5_000;

    private boolean cAdvisorEnabled = true;

    private String cAdvisorUrl = "http://cadvisor:8080";

    @Min(250)
    private long cAdvisorConnectTimeoutMs = 500;

    @Min(16_384)
    @Max(16_000_000)
    private long cAdvisorMaxResponseBytes = 8_000_000;

    @Min(1_000)
    private long sseTimeoutMs = 1_800_000;

    @Min(1)
    @Max(1_000)
    private int maxIncidents = 100;

    @Min(1)
    @Max(100)
    private double warningCpuPercent = 75;

    @Min(1)
    @Max(100)
    private double criticalCpuPercent = 90;

    @Min(1)
    @Max(100)
    private double warningMemoryPercent = 75;

    @Min(1)
    @Max(100)
    private double criticalMemoryPercent = 90;

    @Min(1)
    @Max(100)
    private double warningDiskPercent = 75;

    @Min(1)
    @Max(100)
    private double criticalDiskPercent = 90;

    @AssertTrue(message = "critical thresholds must be greater than warning thresholds")
    public boolean isThresholdOrderValid() {
        return criticalCpuPercent > warningCpuPercent
                && criticalMemoryPercent > warningMemoryPercent
                && criticalDiskPercent > warningDiskPercent;
    }

    @AssertTrue(message = "operation history snapshot size cannot exceed history capacity")
    public boolean isHistoryBoundValid() {
        return operationHistoryInSnapshot <= operationHistoryCapacity;
    }
}
