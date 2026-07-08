package com.vibegraph.graph.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.service.impl.GraphServiceImpl;

/**
 * Unit tests for GraphServiceImpl against the current Sprint 1 API
 * (getFullGraph, searchNodes). The service delegates to GraphRepository, so we
 * mock the repository (no Neo4j) and verify delegation + pass-through.
 *
 * Run: mvn test -Dtest=GraphServiceTest
 */
@DisplayName("GraphService")
class GraphServiceTest {

    private GraphRepository repository;
    private GraphService graphService;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(GraphRepository.class);
        graphService = new GraphServiceImpl(repository);
    }

    @Test
    @DisplayName("getFullGraph delegates to the repository and returns its result")
    void getFullGraphDelegates() {
        GraphDataResponse expected = GraphDataResponse.builder()
                .nodes(List.of(NodeDto.builder()
                        .id("n1").type("Class").name("Foo").fullName("com.example.Foo").build()))
                .edges(List.of())
                .nodeStats(Map.of("Class", 1))
                .edgeStats(Map.of())
                .build();
        when(repository.getFullGraph("p1")).thenReturn(expected);

        GraphDataResponse result = graphService.getFullGraph("p1");

        assertThat(result).isSameAs(expected);
        assertThat(result.getNodes()).hasSize(1);
        verify(repository).getFullGraph("p1");
    }

    @Test
    @DisplayName("searchNodes delegates to the repository and returns its result")
    void searchNodesDelegates() {
        List<NodeDto> hits = List.of(NodeDto.builder()
                .id("n1").type("Class").name("UserService").fullName("com.example.UserService").build());
        when(repository.searchNodes("p1", "User")).thenReturn(hits);

        List<NodeDto> result = graphService.searchNodes("p1", "User");

        assertThat(result).isEqualTo(hits);
        verify(repository).searchNodes("p1", "User");
    }

    @Test
    @DisplayName("getNodeDetail delegates to the repository with the requested hop count")
    void getNodeDetailDelegates() {
        NodeDetailResponse expected = NodeDetailResponse.builder()
                .node(NodeDto.builder()
                        .id("n1")
                        .type("Class")
                        .name("OrderService")
                        .fullName("com.example.OrderService")
                        .build())
                .incoming(List.of())
                .outgoing(List.of())
                .build();
        when(repository.getNodeDetail("p1", "n1", 2)).thenReturn(expected);

        NodeDetailResponse result = graphService.getNodeDetail("p1", "n1", 2);

        assertThat(result).isSameAs(expected);
        verify(repository).getNodeDetail("p1", "n1", 2);
    }

    @Test
    @DisplayName("getNodeDetail rejects unsupported hop counts before repository access")
    void getNodeDetailRejectsUnsupportedHopCounts() {
        assertThatThrownBy(() -> graphService.getNodeDetail("p1", "n1", 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hops");
    }

    @Test
    @DisplayName("getImpactAnalysis delegates to the repository with bounded depth")
    void getImpactAnalysisDelegates() {
        ImpactAnalysisResponse expected = ImpactAnalysisResponse.builder()
                .target(NodeDto.builder()
                        .id("com.example.OrderService")
                        .type("Class")
                        .name("OrderService")
                        .fullName("com.example.OrderService")
                        .build())
                .riskLevel("LOW")
                .directDependents(1)
                .totalDependents(1)
                .willBreak(List.of(NodeDto.builder()
                        .id("com.example.OrderController")
                        .type("Class")
                        .name("OrderController")
                        .fullName("com.example.OrderController")
                        .build()))
                .likelyAffected(List.of())
                .mayNeedTesting(List.of())
                .build();
        when(repository.getImpact("p1", "com.example.OrderService", 3, ImpactProfile.DEPENDENCY)).thenReturn(expected);

        ImpactAnalysisResponse result = graphService.getImpactAnalysis("p1", "com.example.OrderService", 3);

        assertThat(result).isSameAs(expected);
        verify(repository).getImpact("p1", "com.example.OrderService", 3, ImpactProfile.DEPENDENCY);
    }

    @Test
    @DisplayName("getImpactAnalysis forwards the selected impact profile")
    void getImpactAnalysisForwardsProfile() {
        ImpactAnalysisResponse expected = ImpactAnalysisResponse.builder()
                .target(NodeDto.builder().id("p").type("Package").name("p").fullName("p").build())
                .riskLevel("LOW")
                .directDependents(1)
                .totalDependents(1)
                .willBreak(List.of())
                .likelyAffected(List.of())
                .mayNeedTesting(List.of())
                .build();
        when(repository.getImpact("p1", "p", 1, ImpactProfile.STRUCTURAL)).thenReturn(expected);

        ImpactAnalysisResponse result = graphService.getImpactAnalysis("p1", "p", 1, ImpactProfile.STRUCTURAL);

        assertThat(result).isSameAs(expected);
        verify(repository).getImpact("p1", "p", 1, ImpactProfile.STRUCTURAL);
    }

    @Test
    @DisplayName("getImpactAnalysis rejects unsupported depths before repository access")
    void getImpactAnalysisRejectsUnsupportedDepths() {
        assertThatThrownBy(() -> graphService.getImpactAnalysis("p1", "n1", 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depth");
    }

    @Test
    @DisplayName("getImpactAnalysis rejects blank and oversized identifiers before repository access")
    void getImpactAnalysisRejectsInvalidIdentifiers() {
        String oversizedNodeId = "a".repeat(513);

        assertThatThrownBy(() -> graphService.getImpactAnalysis(" ", "n1", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId");
        assertThatThrownBy(() -> graphService.getImpactAnalysis("p1", " ", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId");
        assertThatThrownBy(() -> graphService.getImpactAnalysis("p1", oversizedNodeId, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId");
    }
}
