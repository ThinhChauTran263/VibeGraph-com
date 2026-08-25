package com.vibegraph.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.repository.ProjectMetadata;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.mcp.service.impl.ArchitectureAnalyzerImpl;
import com.vibegraph.mcp.service.impl.ClassContextAnalyzerImpl;
import com.vibegraph.mcp.service.impl.LayerPatternAnalyzerImpl;
import com.vibegraph.mcp.source.SourceGraphSupport;

class McpLimitEnforcementTest {

    private final GraphService graphService = mock(GraphService.class);

    @Test
    void sourceGraphSupport_usesConfiguredNodeLimit() {
        when(graphService.getFullGraph("project")).thenReturn(graphWithNodes(2));
        McpLimitProperties limits = limits(1, 10);

        SourceGraphSupport support = new SourceGraphSupport(graphService, limits);

        assertThat(support.load("project")).isNull();
    }

    @Test
    void sourceGraphSupport_usesConfiguredEdgeLimit() {
        when(graphService.getFullGraph("project")).thenReturn(graphWithEdges(2));
        McpLimitProperties limits = limits(10, 1);

        SourceGraphSupport support = new SourceGraphSupport(graphService, limits);

        assertThat(support.load("project")).isNull();
    }

    @Test
    void classContextAnalyzer_usesConfiguredNodeLimit() {
        when(graphService.getFullGraph("project")).thenReturn(graphWithNodes(2));
        McpLimitProperties limits = limits(1, 10);

        var response = new ClassContextAnalyzerImpl(graphService, limits)
                .analyzeClass("project", "Foo");

        assertThat(response.getWarnings()).containsExactly("Graph is too large for class context: 2 nodes, 0 edges.");
    }

    @Test
    void layerPatternAnalyzer_usesConfiguredNodeLimit() {
        when(graphService.getFullGraph("project")).thenReturn(graphWithNodes(2));
        McpLimitProperties limits = limits(1, 10);

        var response = new LayerPatternAnalyzerImpl(graphService, limits)
                .analyzeLayer("project", "SERVICE");

        assertThat(response.getWarnings()).containsExactly("Graph is too large for layer pattern: 2 nodes, 0 edges.");
    }

    @Test
    void oversizedMetadataRejectsMcpBeforeTheFullGraphIsLoaded() {
        when(graphService.getProjectMetadata("project"))
                .thenReturn(new ProjectMetadata("project", "Demo", "/tmp", null, null, 1, 101, 201));
        McpLimitProperties limits = limits(100, 200);

        assertThat(new SourceGraphSupport(graphService, limits).load("project")).isNull();
        assertThat(new ClassContextAnalyzerImpl(graphService, limits)
                .analyzeClass("project", "Foo").getWarnings()).containsExactly(
                        "Graph is too large for class context: 101 nodes, 201 edges.");
        assertThat(new LayerPatternAnalyzerImpl(graphService, limits)
                .analyzeLayer("project", "SERVICE").getWarnings()).containsExactly(
                        "Graph is too large for layer pattern: 101 nodes, 201 edges.");
        assertThatThrownBy(() -> new ArchitectureAnalyzerImpl(graphService, limits)
                .analyzeProject("project"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MCP safety limit");
        verify(graphService, never()).getFullGraph("project");
    }

    private McpLimitProperties limits(int maxNodes, int maxEdges) {
        McpLimitProperties limits = new McpLimitProperties();
        limits.setMaxNodes(maxNodes);
        limits.setMaxEdges(maxEdges);
        return limits;
    }

    private GraphDataResponse graphWithNodes(int count) {
        List<NodeDto> nodes = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> NodeDto.builder()
                        .id("node-" + index)
                        .type("Class")
                        .name("Service" + index)
                        .fullName("com.example.Service" + index)
                        .properties(java.util.Map.of("springLayer", "SERVICE"))
                        .build())
                .toList();
        return GraphDataResponse.builder().nodes(nodes).edges(List.of()).build();
    }

    private GraphDataResponse graphWithEdges(int count) {
        List<EdgeDto> edges = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> EdgeDto.builder()
                        .id("edge-" + index)
                        .source("source-" + index)
                        .target("target-" + index)
                        .type("CALLS")
                        .build())
                .toList();
        return GraphDataResponse.builder().nodes(List.of()).edges(edges).build();
    }
}
