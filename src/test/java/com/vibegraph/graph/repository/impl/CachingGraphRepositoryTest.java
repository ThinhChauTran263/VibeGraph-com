package com.vibegraph.graph.repository.impl;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.repository.impl.neo4j.Neo4jGraphRepository;

/**
 * B-L2: the overflow prune must keep exactly MAX_ENTRIES snapshots and evict the
 * oldest ones first. The clock seam makes eviction order deterministic (a real
 * millisecond clock can stamp several puts with the same instant).
 */
class CachingGraphRepositoryTest {

    @Test
    @DisplayName("loading MAX_ENTRIES + 3 projects keeps MAX_ENTRIES snapshots and evicts the 3 oldest")
    void pruneEvictsOldestSnapshotsWhenOverflowing() {
        Neo4jGraphRepository delegate = mock(Neo4jGraphRepository.class);
        GraphDataResponse graph = mock(GraphDataResponse.class);
        when(delegate.getFullGraph(anyString())).thenReturn(graph);

        AtomicLong tick = new AtomicLong(1_000_000L);
        CachingGraphRepository repository = new CachingGraphRepository(delegate, tick::incrementAndGet);

        int total = CachingGraphRepository.MAX_ENTRIES + 3;
        for (int i = 0; i < total; i++) {
            repository.getFullGraph("p" + i);
        }

        // The three oldest (p0, p1, p2) were evicted during the inserts: re-reading
        // each of them must hit the delegate a SECOND time.
        repository.getFullGraph("p0");
        repository.getFullGraph("p1");
        repository.getFullGraph("p2");
        verify(delegate, times(2)).getFullGraph("p0");
        verify(delegate, times(2)).getFullGraph("p1");
        verify(delegate, times(2)).getFullGraph("p2");

        // The most recent snapshots stayed cached: still exactly one delegate call each.
        repository.getFullGraph("p" + (total - 1));
        repository.getFullGraph("p" + (total - 2));
        verify(delegate, times(1)).getFullGraph("p" + (total - 1));
        verify(delegate, times(1)).getFullGraph("p" + (total - 2));
    }
}
