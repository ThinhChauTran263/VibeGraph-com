package com.vibegraph.mcp.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentTaskRetentionBatchTest {

    @Mock
    private AgentTaskRepository repository;

    @Test
    void pruneBatchDeletesOnlyIdsClaimedByRepository() {
        Instant cutoff = Instant.parse("2026-07-01T00:00:00Z");
        when(repository.findPrunableTaskIds(cutoff, 50)).thenReturn(List.of("done-1", "done-2"));
        when(repository.deleteClaimedTaskIds(List.of("done-1", "done-2"))).thenReturn(2);
        AgentTaskRetentionBatch batch = new AgentTaskRetentionBatch(repository);

        int deleted = batch.pruneBatch(cutoff, 50);

        assertThat(deleted).isEqualTo(2);
        verify(repository).findPrunableTaskIds(cutoff, 50);
        verify(repository).deleteClaimedTaskIds(List.of("done-1", "done-2"));
    }

    @Test
    void pruneBatchReturnsZeroWithoutIssuingDeleteWhenNoLeafIsClaimed() {
        when(repository.findPrunableTaskIds(any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        AgentTaskRetentionBatch batch = new AgentTaskRetentionBatch(repository);

        assertThat(batch.pruneBatch(Instant.now(), 50)).isZero();

        verify(repository, org.mockito.Mockito.never()).deleteClaimedTaskIds(any());
    }
}
