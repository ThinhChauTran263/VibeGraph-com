package com.vibegraph.graph.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;

/**
 * Web-layer tests for GraphController using standalone MockMvc — no Neo4j and no
 * full Spring context (mirrors ProjectControllerTest). Covers the Sprint 1
 * vertical-slice endpoint GET /api/projects/{id}/graph and its ApiResponse wrapper.
 *
 * Run: mvn test -Dtest=GraphControllerTest
 */
@DisplayName("GraphController")
class GraphControllerTest {

    private MockMvc mockMvc;
    private GraphService graphService;

    @BeforeEach
    void setUp() {
        graphService = Mockito.mock(GraphService.class);
        GraphController controller = new GraphController(graphService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph returns the wrapped full graph")
    void shouldReturnFullGraph() throws Exception {
        GraphDataResponse response = GraphDataResponse.builder()
                .nodes(List.of(NodeDto.builder()
                        .id("com.example.UserService")
                        .type("Class")
                        .name("UserService")
                        .fullName("com.example.UserService")
                        .build()))
                .edges(List.of(EdgeDto.builder()
                        .id("com.example.UserService|CALLS|com.example.UserRepository.find()")
                        .source("com.example.UserService")
                        .target("com.example.UserRepository.find()")
                        .type("CALLS")
                        .build()))
                .nodeStats(Map.of("Class", 1))
                .edgeStats(Map.of("CALLS", 1))
                .build();
        when(graphService.getFullGraph("p1")).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodes[0].type").value("Class"))
                .andExpect(jsonPath("$.data.nodes[0].fullName").value("com.example.UserService"))
                .andExpect(jsonPath("$.data.edges[0].type").value("CALLS"))
                .andExpect(jsonPath("$.data.nodeStats.Class").value(1));

        verify(graphService, times(1)).getFullGraph("p1");
    }

    @Test
    @DisplayName("GET graph returns empty arrays for a project with no data")
    void shouldReturnEmptyGraph() throws Exception {
        when(graphService.getFullGraph("empty")).thenReturn(GraphDataResponse.builder()
                .nodes(List.of()).edges(List.of()).nodeStats(Map.of()).edgeStats(Map.of()).build());

        mockMvc.perform(get("/api/projects/empty/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodes").isEmpty());
    }
}
