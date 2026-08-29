package com.vibegraph.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MeteredToolCallbackTelemetryTest {

    @Test
    void graphCounts_readsTopLevelArrays() {
        MeteredToolCallback.GraphCounts counts = MeteredToolCallback.graphCounts(
                "{\"nodes\":[1,2,3],\"edges\":[1,2]}");

        assertThat(counts.nodes()).isEqualTo(3);
        assertThat(counts.edges()).isEqualTo(2);
    }

    @Test
    void graphCounts_readsNestedCountMetadata() {
        MeteredToolCallback.GraphCounts counts = MeteredToolCallback.graphCounts(
                "{\"data\":{\"nodeCount\":42,\"edgeCount\":77}}");

        assertThat(counts.nodes()).isEqualTo(42);
        assertThat(counts.edges()).isEqualTo(77);
    }

    @Test
    void graphCounts_returnsZeroForNonGraphText() {
        MeteredToolCallback.GraphCounts counts = MeteredToolCallback.graphCounts("plain text response");

        assertThat(counts.nodes()).isZero();
        assertThat(counts.edges()).isZero();
    }
}
