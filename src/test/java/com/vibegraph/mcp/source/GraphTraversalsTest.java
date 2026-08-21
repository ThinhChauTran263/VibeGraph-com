package com.vibegraph.mcp.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;

class GraphTraversalsTest {

    @Test
    void reachableSymbolsReturnsOnlyBoundedReverseImpactSet() {
        GraphView graph = new GraphView(
                List.of(
                        node("changed", "Method", "app.Service.changed"),
                        node("caller", "Method", "app.Controller.caller"),
                        node("route", "Method", "app.Route.route"),
                        node("unrelated", "Method", "app.Other.unrelated")),
                List.of(
                        edge("caller", "changed", "CALLS"),
                        edge("route", "caller", "STEP_IN_FLOW"),
                        edge("unrelated", "changed", "READS")));

        List<GraphTraversals.ReachableSymbol> reachable =
                GraphTraversals.reachableSymbols(graph, Set.of("changed"), 3, 10);

        assertThat(reachable)
                .extracting(GraphTraversals.ReachableSymbol::id)
                .containsExactly("changed", "caller", "route");
        assertThat(reachable)
                .extracting(GraphTraversals.ReachableSymbol::depth)
                .containsExactly(0, 1, 2);
    }

    @Test
    void reachableSymbolsHonorsDepthAndResultCaps() {
        GraphView graph = new GraphView(
                List.of(
                        node("a", "Method", "a"),
                        node("b", "Method", "b"),
                        node("c", "Method", "c")),
                List.of(edge("b", "a", "CALLS"), edge("c", "b", "CALLS")));

        assertThat(GraphTraversals.reachableSymbols(graph, Set.of("a"), 1, 10))
                .extracting(GraphTraversals.ReachableSymbol::id)
                .containsExactly("a", "b");
        assertThat(GraphTraversals.reachableSymbols(graph, Set.of("a"), 5, 2))
                .extracting(GraphTraversals.ReachableSymbol::id)
                .containsExactly("a", "b");
    }

    private NodeDto node(String id, String type, String fullName) {
        return NodeDto.builder().id(id).type(type).name(id).fullName(fullName).build();
    }

    private EdgeDto edge(String source, String target, String type) {
        return EdgeDto.builder().id(source + ":" + target).source(source).target(target).type(type).build();
    }
}
