package com.vibegraph.graph.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.NodeNotFoundException;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.graph.config.GraphPayloadProperties;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.impl.GraphArchitectureProjector;
import com.vibegraph.graph.service.impl.GraphPayloadGuard;
import com.vibegraph.graph.service.impl.GraphResponseFilter;

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
    private ProjectOwnershipGuard ownershipGuard;

    @BeforeEach
    void setUp() {
        graphService = Mockito.mock(GraphService.class);
        ownershipGuard = Mockito.mock(ProjectOwnershipGuard.class);
        GraphController controller = new GraphController(
                graphService, new GraphArchitectureProjector(), new GraphResponseFilter(),
                new GraphPayloadGuard(), new GraphPayloadProperties(), ownershipGuard);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph returns 403 when the ownership guard rejects a non-owner")
    void shouldReturn403WhenNotOwner() throws Exception {
        doThrow(new ForbiddenException("Access denied")).when(ownershipGuard).assertOwner("p1");

        mockMvc.perform(get("/api/projects/p1/graph"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(graphService, never()).getFullGraph("p1");
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph returns the baseline graph by default")
    void shouldReturnFullGraph() throws Exception {
        GraphDataResponse response = GraphDataResponse.builder()
                .nodes(List.of(
                        NodeDto.builder()
                                .id("com.example.UserService")
                                .type("Class")
                                .name("UserService")
                                .fullName("com.example.UserService")
                                .filePath("src/main/java/com/example/UserService.java")
                                .build(),
                        NodeDto.builder()
                                .id("com.example.UserService.findById()")
                                .type("Method")
                                .name("findById")
                                .fullName("com.example.UserService.findById()")
                                .filePath("src/main/java/com/example/UserService.java")
                                .build(),
                        NodeDto.builder()
                                .id("com.example.UserService.load()")
                                .type("Method")
                                .name("load")
                                .fullName("com.example.UserService.load()")
                                .filePath("src/main/java/com/example/UserService.java")
                                .build(),
                        NodeDto.builder()
                                .id("com.example.UserRepository")
                                .type("Interface")
                                .name("UserRepository")
                                .fullName("com.example.UserRepository")
                                .filePath("src/main/java/com/example/UserRepository.java")
                                .build(),
                        NodeDto.builder()
                                .id("com.example.UserService.repository")
                                .type("Field")
                                .name("repository")
                                .fullName("com.example.UserService.repository")
                                .build(),
                        NodeDto.builder()
                                .id("com.example")
                                .type("Project")
                                .name("com.example")
                                .fullName("com.example")
                                .build(),
                        NodeDto.builder()
                                .id("com.example.user")
                                .type("Package")
                                .name("user")
                                .fullName("com.example.user")
                                .build(),
                        NodeDto.builder()
                                .id("src/main/java/com/example/UserService.java")
                                .type("File")
                                .name("UserService.java")
                                .fullName("src/main/java/com/example/UserService.java")
                                .filePath("src/main/java/com/example/UserService.java")
                                .build(),
                        NodeDto.builder()
                                .id("src/main/java/com/example/UserRepository.java")
                                .type("File")
                                .name("UserRepository.java")
                                .fullName("src/main/java/com/example/UserRepository.java")
                                .filePath("src/main/java/com/example/UserRepository.java")
                                .build()))
                .edges(List.of(EdgeDto.builder()
                        .id("com.example.UserService.findById()|CALLS|com.example.UserService.load()")
                        .source("com.example.UserService.findById()")
                        .target("com.example.UserService.load()")
                        .type("CALLS")
                        .build(),
                        EdgeDto.builder()
                        .id("com.example|CONTAINS|com.example.user")
                        .source("com.example")
                        .target("com.example.user")
                        .type("CONTAINS")
                        .build(),
                        EdgeDto.builder()
                        .id("com.example.user|CONTAINS|src/main/java/com/example/UserService.java")
                        .source("com.example.user")
                        .target("src/main/java/com/example/UserService.java")
                        .type("CONTAINS")
                        .build(),
                        EdgeDto.builder()
                        .id("src/main/java/com/example/UserService.java|DEFINES|com.example.UserService")
                        .source("src/main/java/com/example/UserService.java")
                        .target("com.example.UserService")
                        .type("DEFINES")
                        .build(),
                        EdgeDto.builder()
                        .id("src/main/java/com/example/UserService.java|DEFINES|com.example.UserService.findById()")
                        .source("src/main/java/com/example/UserService.java")
                        .target("com.example.UserService.findById()")
                        .type("DEFINES")
                        .build(),
                        EdgeDto.builder()
                        .id("com.example.UserService|HAS_METHOD|com.example.UserService.findById()")
                        .source("com.example.UserService")
                        .target("com.example.UserService.findById()")
                        .type("HAS_METHOD")
                        .build(),
                        EdgeDto.builder()
                        .id("com.example.UserService|IMPORTS|com.example.UserRepository")
                        .source("com.example.UserService")
                        .target("com.example.UserRepository")
                        .type("IMPORTS")
                        .lineNumber(3)
                        .build(),
                        EdgeDto.builder()
                        .id("com.example.UserService.repository|TYPE_OF|com.example.UserRepository")
                        .source("com.example.UserService.repository")
                        .target("com.example.UserRepository")
                        .type("TYPE_OF")
                        .build()))
                .nodeStats(Map.of("Class", 1, "Method", 2, "Interface", 1, "Field", 1, "Project", 1, "Package", 1, "File", 2))
                .edgeStats(Map.of("CALLS", 1, "IMPORTS", 1, "TYPE_OF", 1, "CONTAINS", 2, "DEFINES", 2, "HAS_METHOD", 1))
                .build();
        when(graphService.getFullGraph("p1")).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodes.length()").value(6))
                .andExpect(jsonPath("$.data.nodes[0].type").value("Class"))
                .andExpect(jsonPath("$.data.nodes[1].type").value("Method"))
                .andExpect(jsonPath("$.data.edges.length()").value(5))
                .andExpect(jsonPath("$.data.edges[0].type").value("CALLS"))
                .andExpect(jsonPath("$.data.nodeStats.Class").value(1))
                .andExpect(jsonPath("$.data.nodeStats.File").value(2))
                .andExpect(jsonPath("$.data.nodeStats.Method").value(2))
                .andExpect(jsonPath("$.data.nodeStats.Project").doesNotExist())
                .andExpect(jsonPath("$.data.nodeStats.Package").doesNotExist())
                .andExpect(jsonPath("$.data.edgeStats.CONTAINS").doesNotExist())
                .andExpect(jsonPath("$.data.edgeStats.TYPE_OF").doesNotExist())
                .andExpect(jsonPath("$.data.edgeStats.DEFINES").value(1))
                .andExpect(jsonPath("$.data.edges[4].source").value("src/main/java/com/example/UserService.java"))
                .andExpect(jsonPath("$.data.edges[4].target").value("src/main/java/com/example/UserRepository.java"))
                .andExpect(jsonPath("$.data.edges[4].type").value("IMPORTS"))
                // Guardrail metadata is attached even when the graph fits under the limits.
                .andExpect(jsonPath("$.data.meta.truncated").value(false))
                .andExpect(jsonPath("$.data.meta.totalNodes").value(6))
                .andExpect(jsonPath("$.data.meta.returnedNodes").value(6));

        verify(graphService, times(1)).getFullGraph("p1");
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph?mode=deep returns the full graph")
    void shouldReturnDeepGraphWhenRequested() throws Exception {
        GraphDataResponse response = GraphDataResponse.builder()
                .nodes(List.of(
                        NodeDto.builder().id("com.example.UserService").type("Class").name("UserService").fullName("com.example.UserService").build(),
                        NodeDto.builder().id("com.example.UserService.findById()").type("Method").name("findById").fullName("com.example.UserService.findById()").build(),
                        NodeDto.builder().id("com.example.UserService.load()").type("Method").name("load").fullName("com.example.UserService.load()").build(),
                        NodeDto.builder().id("com.example.UserService.repository").type("Field").name("repository").fullName("com.example.UserService.repository").build()))
                .edges(List.of(EdgeDto.builder()
                        .id("com.example.UserService.findById()|CALLS|com.example.UserService.load()")
                        .source("com.example.UserService.findById()")
                        .target("com.example.UserService.load()")
                        .type("CALLS")
                        .build(),
                        EdgeDto.builder()
                                .id("com.example.UserService.repository|TYPE_OF|com.example.UserRepository")
                                .source("com.example.UserService.repository")
                                .target("com.example.UserRepository")
                                .type("TYPE_OF")
                                .build()))
                .nodeStats(Map.of("Class", 1, "Method", 2, "Field", 1))
                .edgeStats(Map.of("CALLS", 1, "TYPE_OF", 1))
                .build();
        when(graphService.getFullGraph("p1")).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph").param("mode", "deep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodes.length()").value(4))
                .andExpect(jsonPath("$.data.edges.length()").value(2))
                .andExpect(jsonPath("$.data.nodes[3].type").value("Field"));

        verify(graphService, times(1)).getFullGraph("p1");
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph?includeDeep=true returns the full graph")
    void shouldReturnDeepGraphWhenIncludeDeepIsTrue() throws Exception {
        GraphDataResponse response = GraphDataResponse.builder()
                .nodes(List.of(
                        NodeDto.builder().id("com.example.UserService").type("Class").name("UserService").fullName("com.example.UserService").build(),
                        NodeDto.builder().id("com.example.UserService.findById()").type("Method").name("findById").fullName("com.example.UserService.findById()").build(),
                        NodeDto.builder().id("com.example.UserService.load()").type("Method").name("load").fullName("com.example.UserService.load()").build(),
                        NodeDto.builder().id("com.example.UserService.repository").type("Field").name("repository").fullName("com.example.UserService.repository").build()))
                .edges(List.of(EdgeDto.builder()
                        .id("com.example.UserService.findById()|CALLS|com.example.UserService.load()")
                        .source("com.example.UserService.findById()")
                        .target("com.example.UserService.load()")
                        .type("CALLS")
                        .build(),
                        EdgeDto.builder()
                                .id("com.example.UserService.repository|TYPE_OF|com.example.UserRepository")
                                .source("com.example.UserService.repository")
                                .target("com.example.UserRepository")
                                .type("TYPE_OF")
                                .build()))
                .nodeStats(Map.of("Class", 1, "Method", 2, "Field", 1))
                .edgeStats(Map.of("CALLS", 1, "TYPE_OF", 1))
                .build();
        when(graphService.getFullGraph("p1")).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph").param("includeDeep", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes.length()").value(4))
                .andExpect(jsonPath("$.data.edges.length()").value(2));
    }

    @Test
    @DisplayName("GET graph filters by normalized packageName and whitelisted edge types")
    void shouldFilterGraphByPackageAndEdgeTypes() throws Exception {
        GraphDataResponse response = GraphDataResponse.builder()
                .nodes(List.of(
                        NodeDto.builder()
                                .id("com.example.service.UserService")
                                .type("Class")
                                .name("UserService")
                                .fullName("com.example.service.UserService")
                                .properties(Map.of("packageName", "com.example.service"))
                                .build(),
                        NodeDto.builder()
                                .id("com.example.service.UserService.run()")
                                .type("Method")
                                .name("run")
                                .fullName("com.example.service.UserService.run()")
                                .properties(Map.of("packageName", "com.example.service"))
                                .build(),
                        NodeDto.builder()
                                .id("com.example.web.UserController")
                                .type("Class")
                                .name("UserController")
                                .fullName("com.example.web.UserController")
                                .properties(Map.of("packageName", "com.example.web"))
                                .build()))
                .edges(List.of(
                        EdgeDto.builder()
                                .id("e1")
                                .source("com.example.service.UserService")
                                .target("com.example.service.UserService.run()")
                                .type("HAS_METHOD")
                                .build(),
                        EdgeDto.builder()
                                .id("e2")
                                .source("com.example.service.UserService.run()")
                                .target("com.example.web.UserController")
                                .type("CALLS")
                                .build()))
                .build();
        when(graphService.getFullGraph("p1")).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph")
                        .param("mode", "deep")
                        .param("packagePath", "com.example.service")
                        .param("edgeTypes", "HAS_METHOD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes.length()").value(2))
                .andExpect(jsonPath("$.data.edges.length()").value(1))
                .andExpect(jsonPath("$.data.edges[0].type").value("HAS_METHOD"))
                .andExpect(jsonPath("$.data.nodeStats.Class").value(1))
                .andExpect(jsonPath("$.data.nodeStats.Method").value(1));
    }

    @Test
    @DisplayName("GET graph rejects invalid edge type filters before returning payload")
    void shouldRejectInvalidEdgeTypeFilter() throws Exception {
        when(graphService.getFullGraph("p1")).thenReturn(GraphDataResponse.builder()
                .nodes(List.of()).edges(List.of()).build());

        mockMvc.perform(get("/api/projects/p1/graph")
                        .param("mode", "deep")
                        .param("edgeTypes", "CALLS,DELETE_ALL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET graph caps the payload and reports truncation when an explicit small limit is requested")
    void shouldCapPayloadWhenRequested() throws Exception {
        List<NodeDto> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            nodes.add(NodeDto.builder().id("c" + i).type("Class").name("C" + i).fullName("C" + i).build());
        }
        when(graphService.getFullGraph("big")).thenReturn(GraphDataResponse.builder()
                .nodes(nodes).edges(List.of()).nodeStats(Map.of("Class", 10)).edgeStats(Map.of()).build());

        mockMvc.perform(get("/api/projects/big/graph").param("nodeLimit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodes.length()").value(3))
                .andExpect(jsonPath("$.data.meta.truncated").value(true))
                .andExpect(jsonPath("$.data.meta.totalNodes").value(10))
                .andExpect(jsonPath("$.data.meta.returnedNodes").value(3))
                .andExpect(jsonPath("$.data.meta.reason").value("GRAPH_TOO_LARGE"));
    }

    @Test
    @DisplayName("GET graph treats zero limits as uncapped")
    void shouldDisablePayloadCapWithZeroLimits() throws Exception {
        List<NodeDto> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            nodes.add(NodeDto.builder().id("c" + i).type("Class").name("C" + i).fullName("C" + i).build());
        }
        when(graphService.getFullGraph("big")).thenReturn(GraphDataResponse.builder()
                .nodes(nodes).edges(List.of()).nodeStats(Map.of("Class", 10)).edgeStats(Map.of()).build());

        mockMvc.perform(get("/api/projects/big/graph")
                        .param("mode", "deep")
                        .param("nodeLimit", "0")
                        .param("edgeLimit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodes.length()").value(10))
                .andExpect(jsonPath("$.data.meta.truncated").value(false))
                .andExpect(jsonPath("$.data.meta.nodeLimit").value(0))
                .andExpect(jsonPath("$.data.meta.edgeLimit").value(0));
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

    @Test
    @DisplayName("GET /api/projects/{id}/graph/neighbors/{nodeId} returns wrapped node detail")
    void shouldReturnNodeDetail() throws Exception {
        NodeDetailResponse response = NodeDetailResponse.builder()
                .node(NodeDto.builder()
                        .id("com.example.OrderService")
                        .type("Class")
                        .name("OrderService")
                        .fullName("com.example.OrderService")
                        .filePath("src/OrderService.java")
                        .lineNumber(12)
                        .build())
                .incoming(List.of(NodeDetailResponse.ConnectionDto.builder()
                        .otherNode(NodeDto.builder()
                                .id("com.example.OrderController")
                                .type("Class")
                                .name("OrderController")
                                .fullName("com.example.OrderController")
                                .build())
                        .relationshipType("CALLS")
                        .direction("INCOMING")
                        .build()))
                .outgoing(List.of(NodeDetailResponse.ConnectionDto.builder()
                        .otherNode(NodeDto.builder()
                                .id("com.example.OrderRepository")
                                .type("Interface")
                                .name("OrderRepository")
                                .fullName("com.example.OrderRepository")
                                .build())
                        .relationshipType("INJECTS")
                        .direction("OUTGOING")
                        .build()))
                .build();
        when(graphService.getNodeDetail("p1", "com.example.OrderService", 1)).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph/neighbors/{nodeId}", "com.example.OrderService")
                        .param("hops", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.node.fullName").value("com.example.OrderService"))
                .andExpect(jsonPath("$.data.incoming[0].relationshipType").value("CALLS"))
                .andExpect(jsonPath("$.data.incoming[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.data.outgoing[0].otherNode.type").value("Interface"));

        verify(graphService, times(1)).getNodeDetail("p1", "com.example.OrderService", 1);
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph/neighbors supports slash-containing node IDs via query parameter")
    void shouldReturnNodeDetailForSlashContainingNodeId() throws Exception {
        NodeDetailResponse response = NodeDetailResponse.builder()
                .node(NodeDto.builder()
                        .id("GET /api/users/{id}")
                        .type("Route")
                        .name("GET /api/users/{id}")
                        .fullName("GET /api/users/{id}")
                        .build())
                .incoming(List.of())
                .outgoing(List.of())
                .build();
        when(graphService.getNodeDetail("p1", "GET /api/users/{id}", 1)).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph/neighbors")
                        .param("nodeId", "GET /api/users/{id}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.node.fullName").value("GET /api/users/{id}"));

        verify(graphService, times(1)).getNodeDetail("p1", "GET /api/users/{id}", 1);
    }

    @Test
    @DisplayName("GET node detail rejects unsupported hop counts")
    void shouldRejectUnsupportedHopCounts() throws Exception {
        when(graphService.getNodeDetail("p1", "com.example.OrderService", 99))
                .thenThrow(new IllegalArgumentException("hops must be one of 0, 1, 2, 3, 5"));

        mockMvc.perform(get("/api/projects/p1/graph/neighbors/{nodeId}", "com.example.OrderService")
                        .param("hops", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET node detail returns 404 when the selected node is missing")
    void shouldReturnNotFoundForMissingNode() throws Exception {
        when(graphService.getNodeDetail("p1", "missing.Node", 1))
                .thenThrow(new NodeNotFoundException("Node not found"));

        mockMvc.perform(get("/api/projects/p1/graph/neighbors/{nodeId}", "missing.Node"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET node detail rejects non-numeric hop counts")
    void shouldRejectNonNumericHopCounts() throws Exception {
        mockMvc.perform(get("/api/projects/p1/graph/neighbors/{nodeId}", "com.example.OrderService")
                        .param("hops", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/projects/{id}/graph/impact returns wrapped blast radius")
    void shouldReturnImpactAnalysis() throws Exception {
        ImpactAnalysisResponse response = ImpactAnalysisResponse.builder()
                .target(NodeDto.builder()
                        .id("com.example.OrderService")
                        .type("Class")
                        .name("OrderService")
                        .fullName("com.example.OrderService")
                        .build())
                .riskLevel("LOW")
                .directDependents(1)
                .totalDependents(2)
                .willBreak(List.of(NodeDto.builder()
                        .id("com.example.OrderController")
                        .type("Class")
                        .name("OrderController")
                        .fullName("com.example.OrderController")
                        .build()))
                .likelyAffected(List.of(NodeDto.builder()
                        .id("com.example.ApiGateway")
                        .type("Class")
                        .name("ApiGateway")
                        .fullName("com.example.ApiGateway")
                        .build()))
                .mayNeedTesting(List.of())
                .build();
        when(graphService.getImpactAnalysis("p1", "com.example.OrderService", 3, ImpactProfile.STRUCTURAL)).thenReturn(response);

        mockMvc.perform(get("/api/projects/p1/graph/impact")
                        .param("nodeId", "com.example.OrderService")
                        .param("depth", "3")
                        .param("profile", "structural"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.target.fullName").value("com.example.OrderService"))
                .andExpect(jsonPath("$.data.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.data.willBreak[0].fullName").value("com.example.OrderController"))
                .andExpect(jsonPath("$.data.likelyAffected[0].fullName").value("com.example.ApiGateway"));

        verify(graphService, times(1)).getImpactAnalysis("p1", "com.example.OrderService", 3, ImpactProfile.STRUCTURAL);
    }

    @Test
    @DisplayName("GET impact rejects unsupported depth counts")
    void shouldRejectUnsupportedImpactDepths() throws Exception {
        when(graphService.getImpactAnalysis("p1", "com.example.OrderService", 99, ImpactProfile.DEPENDENCY))
                .thenThrow(new IllegalArgumentException("depth must be one of 1, 2, 3, 5"));

        mockMvc.perform(get("/api/projects/p1/graph/impact")
                        .param("nodeId", "com.example.OrderService")
                        .param("depth", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET impact rejects unsupported profiles")
    void shouldRejectUnsupportedImpactProfiles() throws Exception {
        mockMvc.perform(get("/api/projects/p1/graph/impact")
                        .param("nodeId", "com.example.OrderService")
                        .param("depth", "3")
                        .param("profile", "everything"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET impact returns 404 when target node is missing")
    void shouldReturnNotFoundForMissingImpactTarget() throws Exception {
        when(graphService.getImpactAnalysis("p1", "missing.Node", 3, ImpactProfile.DEPENDENCY))
                .thenThrow(new NodeNotFoundException("Node not found"));

        mockMvc.perform(get("/api/projects/p1/graph/impact")
                        .param("nodeId", "missing.Node")
                        .param("depth", "3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NODE_NOT_FOUND"));
    }
}
