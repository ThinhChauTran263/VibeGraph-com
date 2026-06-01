package com.vibegraph.graph.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
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
}
