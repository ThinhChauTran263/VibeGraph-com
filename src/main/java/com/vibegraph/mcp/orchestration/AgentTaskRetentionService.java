package com.vibegraph.mcp.orchestration;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Periodically removes only expired, terminal, unreferenced task DAG leaves. */
@Service
@RequiredArgsConstructor
public class AgentTaskRetentionService {

    private final AgentTaskRetentionBatch batch;
    private final AgentTaskRetentionProperties properties;
    private final Clock clock;

    /** Runs the bounded cleanup; a disabled policy is a no-op. */
    @Scheduled(cron = "${vibegraph.mcp.orchestration.retention.cron:0 15 3 * * ?}")
    public void scheduledCleanup() {
        cleanupExpiredTasks();
    }

    /**
     * Runs no more than the configured number of batches. Returning the count makes the operation
     * directly testable and gives callers a truthful metric hook without logging task IDs.
     */
    public int cleanupExpiredTasks() {
        if (!properties.isEnabled()) {
            return 0;
        }
        Instant cutoff = Instant.now(clock).minus(properties.getRetentionDays(), ChronoUnit.DAYS);
        int deleted = 0;
        for (int i = 0; i < properties.getMaxBatches(); i++) {
            int count = batch.pruneBatch(cutoff, properties.getBatchSize());
            deleted += count;
            if (count == 0) {
                break;
            }
        }
        return deleted;
    }
}
