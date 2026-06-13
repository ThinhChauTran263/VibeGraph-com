package com.vibegraph.diagram.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.diagram.dto.response.UseCaseResponse;
import com.vibegraph.diagram.service.impl.MermaidGeneratorServiceImpl;
import com.vibegraph.diagram.service.impl.UseCaseDiagramServiceImpl;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;

@ExtendWith(MockitoExtension.class)
@DisplayName("UseCaseDiagramService")
class UseCaseDiagramServiceTest {

    private static final String PROJECT_ID = "proj-1";

    @Mock
    GraphService graphService;

    private UseCaseDiagramServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UseCaseDiagramServiceImpl(graphService, new MermaidGeneratorServiceImpl());
    }

    private NodeDto route(String id, String name) {
        return NodeDto.builder().id(id).type("Route").name(name).fullName(id).build();
    }

    private NodeDto method(String id, String name) {
        return NodeDto.builder().id(id).type("Method").name(name).fullName(id).build();
    }

    private EdgeDto handles(String methodId, String routeId) {
        return EdgeDto.builder().source(methodId).target(routeId).type("HANDLES_ROUTE").build();
    }

    private void stubGraph(List<NodeDto> nodes, List<EdgeDto> edges) {
        when(graphService.getFullGraph(PROJECT_ID))
                .thenReturn(GraphDataResponse.builder().nodes(nodes).edges(edges).build());
    }

    @Test
    @DisplayName("generates a valid flowchart with HTTP Client actor for a route handler")
    void generatesUseCaseFromRoute() {
        stubGraph(
                List.of(route("GET /api/users", "GET /api/users"),
                        method("com.app.UserController#list()", "list")),
                List.of(handles("com.app.UserController#list()", "GET /api/users")));

        UseCaseResponse response = service.generateUseCaseDiagram(PROJECT_ID);

        assertThat(response.getActors()).containsExactly("HTTP Client");
        assertThat(response.getUseCases()).containsExactly("GET /api/users");
        String mermaid = response.getMermaidSyntax();
        assertThat(mermaid).startsWith("flowchart LR");
        assertThat(mermaid).contains("((\"HTTP Client\"))");
        assertThat(mermaid).contains("[\"GET /api/users\"]");
        assertThat(mermaid).contains(" --> ");
    }

    @Test
    @DisplayName("orders use cases deterministically by route identifier")
    void deterministicOrdering() {
        stubGraph(
                List.of(route("POST /api/users", "POST /api/users"),
                        route("GET /api/users", "GET /api/users"),
                        route("DELETE /api/users/{id}", "DELETE /api/users/{id}")),
                List.of());

        UseCaseResponse first = service.generateUseCaseDiagram(PROJECT_ID);
        UseCaseResponse second = service.generateUseCaseDiagram(PROJECT_ID);

        assertThat(first.getUseCases())
                .containsExactly("DELETE /api/users/{id}", "GET /api/users", "POST /api/users");
        assertThat(first.getMermaidSyntax()).isEqualTo(second.getMermaidSyntax());
    }

    @Test
    @DisplayName("includes orphan Route nodes that have no HANDLES_ROUTE edge")
    void includesOrphanRoutes() {
        stubGraph(List.of(route("GET /health", "GET /health")), List.of());

        UseCaseResponse response = service.generateUseCaseDiagram(PROJECT_ID);

        assertThat(response.getUseCases()).containsExactly("GET /health");
        assertThat(response.getMermaidSyntax()).contains("[\"GET /health\"]");
    }

    @Test
    @DisplayName("returns a valid empty-but-syntactic diagram when there are no routes")
    void emptyGraphProducesValidDiagram() {
        stubGraph(List.of(), List.of());

        UseCaseResponse response = service.generateUseCaseDiagram(PROJECT_ID);

        assertThat(response.getActors()).isEmpty();
        assertThat(response.getUseCases()).isEmpty();
        assertThat(response.getMermaidSyntax()).startsWith("flowchart LR");
        assertThat(response.getMermaidSyntax()).doesNotContain("-->");
    }

    @Test
    @DisplayName("handles a null graph payload without throwing")
    void nullGraphIsGraceful() {
        when(graphService.getFullGraph(PROJECT_ID)).thenReturn(null);

        UseCaseResponse response = service.generateUseCaseDiagram(PROJECT_ID);

        assertThat(response.getUseCases()).isEmpty();
        assertThat(response.getMermaidSyntax()).startsWith("flowchart LR");
    }

    @Test
    @DisplayName("escapes route names with quotes and special characters so syntax stays valid")
    void escapesSpecialCharacters() {
        stubGraph(
                List.of(route("GET /api/\"weird\"/{id}", "GET /api/\"weird\"/{id}")),
                List.of());

        UseCaseResponse response = service.generateUseCaseDiagram(PROJECT_ID);
        String mermaid = response.getMermaidSyntax();

        // Raw double quotes must not survive into the label (would break Mermaid).
        assertThat(mermaid).doesNotContain("\"weird\"");
        assertThat(mermaid).contains("#quot;weird#quot;");
        // Node id must be a safe Mermaid identifier (no spaces/braces/quotes).
        assertThat(mermaid).containsPattern("uc_[A-Za-z0-9_]+\\[");
    }

    @Test
    @DisplayName("deduplicates node ids when distinct routes sanitize to the same id")
    void deduplicatesCollidingIds() {
        stubGraph(
                List.of(route("GET /a-b", "GET /a-b"),
                        route("GET /a_b", "GET /a_b")),
                List.of());

        UseCaseResponse response = service.generateUseCaseDiagram(PROJECT_ID);
        String mermaid = response.getMermaidSyntax();

        // Both routes present, and the colliding sanitized id gets a numeric suffix.
        assertThat(response.getUseCases()).containsExactly("GET /a-b", "GET /a_b");
        assertThat(mermaid).contains("uc_GET_a_b[");
        assertThat(mermaid).contains("uc_GET_a_b_2[");
    }

    @Test
    @DisplayName("falls back to the route id when the Route node name is blank")
    void fallsBackToRouteIdWhenNameBlank() {
        NodeDto blankNamed = NodeDto.builder()
                .id("GET /api/x").type("Route").name("  ").fullName("GET /api/x")
                .properties(Map.of()).build();
        stubGraph(List.of(blankNamed), List.of());

        UseCaseResponse response = service.generateUseCaseDiagram(PROJECT_ID);

        assertThat(response.getUseCases()).containsExactly("GET /api/x");
    }
}
