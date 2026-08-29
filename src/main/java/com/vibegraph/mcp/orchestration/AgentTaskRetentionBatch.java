package com.vibegraph.mcp.orchestration;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Executes one short, lock-skipping retention batch. */
@Service
@RequiredArgsConstructor
public class AgentTaskRetentionBatch {

    private final AgentTaskRepository repository;

    /**
     * Claims and removes at most {@code batchSize} safe terminal leaves in one transaction.
     * PostgreSQL releases the row locks at commit, allowing another replica to process the next
     * batch without waiting on this scheduler.
     */
    @Transactional
    public int pruneBatch(Instant cutoff, int batchSize) {
        List<String> ids = repository.findPrunableTaskIds(cutoff, batchSize);
        if (ids.isEmpty()) {
            return 0;
        }
        return repository.deleteClaimedTaskIds(ids);
    }
}
