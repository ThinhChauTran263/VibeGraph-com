package com.vibegraph.graph.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;

@DisplayName("GraphPayloadGuard - HTTP full-graph payload cap")
class GraphPayloadGuardTest {

    private final GraphPayloadGuard guard = new GraphPayloadGuard();

    private NodeDto node(String id, String type) {
        return NodeDto.builder().id(id).type(type).name(id).fullName(id).build();
    }

    private EdgeDto edge(String source, String target) {
        return EdgeDto.builder().id(source + "|CALLS|" + target).source(source).target(target).type("CALLS").build();
    }

    private GraphDataResponse graph(List<NodeDto> nodes, List<EdgeDto> edges) {
        return GraphDataResponse.builder()
                .nodes(nodes).edges(edges)
                .nodeStats(Map.of()).edgeStats(Map.of())
                .build();
    }

    @Test
    @DisplayName("a graph under both limits is returned untruncated with truthful meta")
    void underLimit() {
        GraphDataResponse capped = guard.cap(
                graph(List.of(node("a", "Class"), node("b", "Method")), List.of(edge("a", "b"))), 10, 10);

        assertThat(capped.getMeta().isTruncated()).isFalse();
        assertThat(capped.getMeta().getTotalNodes()).isEqualTo(2);
        assertThat(capped.getMeta().getReturnedNodes()).isEqualTo(2);
        assertThat(capped.getMeta().getTotalEdges()).isEqualTo(1);
        assertThat(capped.getMeta().getReturnedEdges()).isEqualTo(1);
        assertThat(capped.getMeta().getReason()).isNull();
        assertThat(capped.getNodes()).hasSize(2);
    }

    @Test
    @DisplayName("caps nodes above the node limit and flags truncation")
    void capsNodes() {
        List<NodeDto> nodes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            nodes.add(node("m" + i, "Method"));
        }
        GraphDataResponse capped = guard.cap(graph(nodes, List.of()), 5, 100);

        assertThat(capped.getNodes()).hasSize(5);
        assertThat(capped.getMeta().isTruncated()).isTrue();
        assertThat(capped.getMeta().getTotalNodes()).isEqualTo(20);
        assertThat(capped.getMeta().getReturnedNodes()).isEqualTo(5);
        assertThat(capped.getMeta().getReason()).isEqualTo("GRAPH_TOO_LARGE");
    }

    @Test
    @DisplayName("caps edges above the edge limit and flags truncation")
    void capsEdges() {
        List<NodeDto> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            nodes.add(node("n" + i, "Class"));
        }
        List<EdgeDto> edges = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            edges.add(edge("n" + i, "n" + (i + 1)));
        }
        GraphDataResponse capped = guard.cap(graph(nodes, edges), 100, 3);

        assertThat(capped.getEdges()).hasSize(3);
        assertThat(capped.getMeta().isTruncated()).isTrue();
        assertThat(capped.getMeta().getTotalEdges()).isEqualTo(9);
        assertThat(capped.getMeta().getReturnedEdges()).isEqualTo(3);
    }

    @Test
    @DisplayName("keeps higher-priority node types when capping")
    void keepsHigherPriorityTypes() {
        List<NodeDto> nodes = List.of(
                node("field1", "Field"),
                node("class1", "Class"),
                node("field2", "Field"),
                node("pkg1", "Package"));
        GraphDataResponse capped = guard.cap(graph(nodes, List.of()), 2, 100);

        List<String> kept = capped.getNodes().stream().map(NodeDto::getId).toList();
        assertThat(kept).containsExactlyInAnyOrder("pkg1", "class1");
    }

    @Test
    @DisplayName("drops edges whose endpoints were removed by the node cap")
    void dropsDanglingEdges() {
        List<NodeDto> nodes = List.of(node("class1", "Class"), node("field1", "Field"));
        GraphDataResponse capped = guard.cap(graph(nodes, List.of(edge("class1", "field1"))), 1, 100);

        assertThat(capped.getNodes()).extracting(NodeDto::getId).containsExactly("class1");
        assertThat(capped.getEdges()).isEmpty();
        assertThat(capped.getMeta().getReturnedEdges()).isZero();
        assertThat(capped.getMeta().getTotalEdges()).isEqualTo(1);
    }

    @Test
    @DisplayName("selection is deterministic across repeated calls")
    void deterministic() {
        List<NodeDto> nodes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            nodes.add(node("m" + i, "Method"));
        }
        List<String> first = guard.cap(graph(nodes, List.of()), 7, 100)
                .getNodes().stream().map(NodeDto::getId).toList();
        List<String> second = guard.cap(graph(nodes, List.of()), 7, 100)
                .getNodes().stream().map(NodeDto::getId).toList();

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("a null graph yields an empty, untruncated payload with zeroed meta")
    void nullGraphIsGraceful() {
        GraphDataResponse capped = guard.cap(null, 1500, 4000);

        assertThat(capped.getNodes()).isEmpty();
        assertThat(capped.getEdges()).isEmpty();
        assertThat(capped.getMeta().isTruncated()).isFalse();
        assertThat(capped.getMeta().getTotalNodes()).isZero();
    }

    @Test
    @DisplayName("ties broken by degree keep the more connected node")
    void tieBrokenByDegree() {
        List<NodeDto> nodes = List.of(node("hub", "Method"), node("leaf", "Method"), node("other", "Method"));
        List<EdgeDto> edges = List.of(edge("hub", "leaf"), edge("hub", "other"));
        GraphDataResponse capped = guard.cap(graph(nodes, edges), 1, 100);

        assertThat(capped.getNodes()).extracting(NodeDto::getId).containsExactly("hub");
    }
}
