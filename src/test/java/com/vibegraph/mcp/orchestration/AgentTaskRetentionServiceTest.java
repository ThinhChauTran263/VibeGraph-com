package com.vibegraph.mcp.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentTaskRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Mock
    private AgentTaskRetentionBatch batch;

    private AgentTaskRetentionProperties properties;
    private AgentTaskRetentionService service;

    @BeforeEach
    void setUp() {
        properties = new AgentTaskRetentionProperties();
        properties.setRetentionDays(30);
        properties.setBatchSize(2);
        properties.setMaxBatches(3);
        service = new AgentTaskRetentionService(
                batch, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void cleanupStopsWhenBatchIsEmptyAndUsesConfiguredCutoff() {
        when(batch.pruneBatch(Instant.parse("2026-07-23T12:00:00Z"), 2))
                .thenReturn(2, 1, 0);

        int deleted = service.cleanupExpiredTasks();

        assertThat(deleted).isEqualTo(3);
        InOrder order = inOrder(batch);
        order.verify(batch, org.mockito.Mockito.times(3))
                .pruneBatch(Instant.parse("2026-07-23T12:00:00Z"), 2);
    }

    @Test
    void cleanupHonorsMaximumBatchBound() {
        when(batch.pruneBatch(Instant.parse("2026-07-23T12:00:00Z"), 2))
                .thenReturn(2);

        int deleted = service.cleanupExpiredTasks();

        assertThat(deleted).isEqualTo(6);
        verify(batch, org.mockito.Mockito.times(3))
                .pruneBatch(Instant.parse("2026-07-23T12:00:00Z"), 2);
    }

    @Test
    void disabledCleanupDoesNotTouchRepository() {
        properties.setEnabled(false);

        assertThat(service.cleanupExpiredTasks()).isZero();

        verify(batch, never()).pruneBatch(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void partialLeafBatchContinuesSoParentsCanBecomeLeaves() {
        properties.setBatchSize(50);
        when(batch.pruneBatch(Instant.parse("2026-07-23T12:00:00Z"), 50))
                .thenReturn(1, 1, 0);

        assertThat(service.cleanupExpiredTasks()).isEqualTo(2);

        verify(batch, org.mockito.Mockito.times(3))
                .pruneBatch(Instant.parse("2026-07-23T12:00:00Z"), 50);
    }
}
