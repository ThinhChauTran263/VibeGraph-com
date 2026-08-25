package com.vibegraph.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.vibegraph.infrastructure.config.InfrastructureMonitorProperties;

class InfrastructureMonitorPropertiesTest {

    @Test
    void defaultThresholdsAreOrderedAndHistoryIsBounded() {
        InfrastructureMonitorProperties properties = new InfrastructureMonitorProperties();

        assertTrue(properties.isThresholdOrderValid());
        assertTrue(properties.isHistoryBoundValid());
        assertTrue(properties.getSseTimeoutMs() > 0);
    }

    @Test
    void invalidThresholdOrderIsRejected() {
        InfrastructureMonitorProperties properties = new InfrastructureMonitorProperties();
        properties.setWarningCpuPercent(95);
        properties.setCriticalCpuPercent(90);

        assertFalse(properties.isThresholdOrderValid());
    }
}
