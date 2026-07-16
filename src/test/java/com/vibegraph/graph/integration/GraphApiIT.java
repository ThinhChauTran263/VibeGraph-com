package com.vibegraph.graph.integration;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.NodeNotFoundException;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.graph.config.GraphPayloadProperties;
import com.vibegraph.graph.controller.GraphController;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.impl.GraphPayloadGuard;

/**
 * Web-layer tests for the Graph API using standalone MockMvc — no Neo4j and no full
 * Spring context (mirrors {@code GraphControllerTest}). Verifies the controller-to-service
 * contract, the {@code ApiResponse} envelope incl. payload meta, and the
 * exception-to-HTTP mapping via {@link GlobalExceptionHandler}.
 */
@DisplayName("Graph API Integration")
class GraphApiIT {

    private MockMvc mockMvc;
    private GraphService graphService;
    private ProjectOwnershipGuard ownershipGuard;

    @BeforeEach
    void setUp() {
        graphService = Mockito.mock(GraphService.class);
        ownershipGuard = Mockito.mock(ProjectOwnershipGuard.class);
        GraphController controller = new GraphController(
                graphService, new GraphPayloadGuard(), new GraphPayloadProperties(), ownershipGuard);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph returns full graph with meta")
    void getFullGraph() throws Exception {
        GraphDataResponse response = GraphDataResponse.builder()
                .nodes(List.of(NodeDto.builder().id("n1").type("Class").name("C1").fullName("com.example.C1").build()))
                .edges(List.of(EdgeDto.builder().id("e1").source("n1").target("n2").type("CALLS").build()))
                .nodeStats(Map.of("Class", 1))
                .edgeStats(Map.of("CALLS", 1))
                .build();
        when(graphService.getFullGraph("p1")).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodes[0].id").value("n1"))
                .andExpect(jsonPath("$.data.meta.truncated").value(false))
                .andExpect(jsonPath("$.data.meta.totalNodes").value(1));
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph/neighbors/{nodeId} returns node detail")
    void getNodeDetail() throws Exception {
        NodeDetailResponse response = NodeDetailResponse.builder()
                .node(NodeDto.builder().id("n1").fullName("com.example.C1").build())
                .incoming(List.of())
                .outgoing(List.of())
                .build();
        when(graphService.getNodeDetail("p1", "com.example.C1", 1)).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph/neighbors/com.example.C1")
                        .param("hops", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.node.fullName").value("com.example.C1"));
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph/impact returns blast radius")
    void getImpactAnalysis() throws Exception {
        ImpactAnalysisResponse response = ImpactAnalysisResponse.builder()
                .target(NodeDto.builder().id("n1").fullName("com.example.C1").build())
                .riskLevel("LOW")
                .directDependents(1)
                .totalDependents(1)
                .willBreak(List.of())
                .likelyAffected(List.of())
                .mayNeedTesting(List.of())
                .build();
        when(graphService.getImpactAnalysis(eq("p1"), eq("com.example.C1"), eq(3), any(ImpactProfile.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph/impact")
                        .param("nodeId", "com.example.C1")
                        .param("depth", "3")
                        .param("profile", "dependency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("LOW"));
    }

    @Test
    @DisplayName("404 Mapping: Node not found")
    void nodeNotFound() throws Exception {
        when(graphService.getNodeDetail("p1", "missing", 1))
                .thenThrow(new NodeNotFoundException("Node not found"));

        mockMvc.perform(get("/api/projects/p1/graph/neighbors/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("400 Mapping: Bad request (invalid depth)")
    void badRequest() throws Exception {
        when(graphService.getImpactAnalysis(eq("p1"), eq("c1"), eq(99), any()))
                .thenThrow(new IllegalArgumentException("depth must be one of 1, 2, 3, 5"));

        mockMvc.perform(get("/api/projects/p1/graph/impact")
                        .param("nodeId", "c1")
                        .param("depth", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
