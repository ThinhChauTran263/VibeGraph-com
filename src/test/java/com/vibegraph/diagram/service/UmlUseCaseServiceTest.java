package com.vibegraph.diagram.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse;
import com.vibegraph.diagram.service.impl.UmlUseCaseRenderer;
import com.vibegraph.diagram.service.impl.UseCaseDiagramServiceImpl;
import com.vibegraph.diagram.service.impl.UseCaseInferenceEngine;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.ProjectService;

@ExtendWith(MockitoExtension.class)
@DisplayName("UseCaseDiagramService - UML Use Case (canonical business model)")
class UmlUseCaseServiceTest {

    private static final String PROJECT_ID = "proj-1";

    @Mock
    GraphService graphService;

    @Mock
    ProjectService projectService;

    private UseCaseDiagramServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UseCaseDiagramServiceImpl(graphService, projectService,
                new UseCaseInferenceEngine(), new UmlUseCaseRenderer(),
                new com.vibegraph.diagram.service.impl.BaLabelBeautifier(),
                new com.vibegraph.diagram.service.impl.SrsUseCaseEnricher());
        lenient().when(projectService.getProject(PROJECT_ID))
                .thenReturn(ProjectResponse.builder().id(PROJECT_ID).name("Shop").status("ANALYZED").build());
    }

    private NodeDto endpoint(String method, String path) {
        return NodeDto.builder()
                .id(method + " " + path)
                .type("APIEndpoint")
                .name(method + " " + path)
                .fullName(method + " " + path)
                .properties(Map.of("httpMethod", method, "routePath", path))
                .build();
    }

    private EdgeDto handles(String controllerFqcn, String method, String path) {
        return EdgeDto.builder()
                .source(controllerFqcn)
                .target(method + " " + path)
                .type("HANDLES_ROUTE")
                .build();
    }

    private void stubGraph(List<NodeDto> nodes, List<EdgeDto> edges) {
        lenient().when(graphService.getFullGraph(PROJECT_ID))
                .thenReturn(GraphDataResponse.builder().nodes(nodes).edges(edges).build());
    }

    @Test
    @DisplayName("collapses a write-capable domain into one business goal: Manage Products")
    void writeDomainBecomesManageGoal() {
        stubGraph(
                List.of(endpoint("GET", "/api/products"),
                        endpoint("GET", "/api/products/{id}"),
                        endpoint("POST", "/api/products"),
                        endpoint("PUT", "/api/products/{id}"),
                        endpoint("DELETE", "/api/products/{id}")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.ProductController#get()", "GET", "/api/products/{id}"),
                        handles("com.app.ProductController#create()", "POST", "/api/products"),
                        handles("com.app.ProductController#update()", "PUT", "/api/products/{id}"),
                        handles("com.app.ProductController#delete()", "DELETE", "/api/products/{id}")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getStyle()).isEqualTo("uml");
        assertThat(res.getSystemName()).isEqualTo("Shop System");
        // One business goal for the whole domain, never raw CRUD endpoints.
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("Manage Products");
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .noneMatch(n -> n.startsWith("View product") || n.startsWith("Create ")
                        || n.startsWith("Update ") || n.startsWith("Delete "));
        // Never the API-map actor.
        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .doesNotContain("HTTP Client");
    }

    @Test
    @DisplayName("the model is identical regardless of requested mode")
    void modeIndependentOutput() {
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("POST", "/api/products")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.ProductController#create()", "POST", "/api/products")));

        UmlUseCaseResponse detailed = service.generateUmlUseCase(PROJECT_ID, "detailed");
        UmlUseCaseResponse grouped = service.generateUmlUseCase(PROJECT_ID, "grouped");

        assertThat(detailed.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("Manage Products");
        assertThat(detailed.getPlantUmlSyntax()).isEqualTo(grouped.getPlantUmlSyntax());
        assertThat(detailed.getMermaidSyntax()).isEqualTo(grouped.getMermaidSyntax());
    }

    @Test
    @DisplayName("a read-only domain becomes a View goal with a single direct association")
    void readOnlyDomainBecomesViewGoal() {
        // Both endpoints are plain GETs -> single User actor, so no Admin/User generalization noise.
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("GET", "/api/products/{id}")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.ProductController#get()", "GET", "/api/products/{id}")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("View Products");
        assertThat(res.getRelations()).anyMatch(r -> "association".equals(r.getType()));
        assertThat(res.getRelations()).noneMatch(r -> "include".equals(r.getType()));
        assertThat(res.getRelations()).noneMatch(r -> "generalization".equals(r.getType()));
        assertThat(res.getPlantUmlSyntax()).doesNotContain("<<include>>");
        assertThat(res.getPlantUmlSyntax()).doesNotContain("--|>");
    }

    @Test
    @DisplayName("builds the full actor generalization chain Guest <|-- User <|-- Admin")
    void fullActorGeneralizationChain() {
        // Guest (register), Registered User (plain GET) and Administrator (/admin POST) all present.
        stubGraph(
                List.of(endpoint("POST", "/api/auth/register"),
                        endpoint("GET", "/api/products"),
                        endpoint("POST", "/admin/orders")),
                List.of(handles("com.app.AuthController#register()", "POST", "/api/auth/register"),
                        handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.AdminOrderController#create()", "POST", "/admin/orders")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        UmlUseCaseResponse.Actor guest = res.getActors().stream()
                .filter(a -> "Guest".equals(a.getName())).findFirst().orElseThrow();
        UmlUseCaseResponse.Actor user = res.getActors().stream()
                .filter(a -> "Registered User".equals(a.getName())).findFirst().orElseThrow();
        UmlUseCaseResponse.Actor admin = res.getActors().stream()
                .filter(a -> "Administrator".equals(a.getName())).findFirst().orElseThrow();

        // Registered User generalizes Guest, Administrator generalizes Registered User.
        assertThat(res.getRelations()).anyMatch(r -> "generalization".equals(r.getType())
                && user.getId().equals(r.getFrom()) && guest.getId().equals(r.getTo()));
        assertThat(res.getRelations()).anyMatch(r -> "generalization".equals(r.getType())
                && admin.getId().equals(r.getFrom()) && user.getId().equals(r.getTo()));
        assertThat(res.getPlantUmlSyntax()).contains(user.getId() + " --|> " + guest.getId());
        assertThat(res.getPlantUmlSyntax()).contains(admin.getId() + " --|> " + user.getId());
    }

    @Test
    @DisplayName("Admin is a generalization of User when both actors are present")
    void adminGeneralizesUser() {
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("POST", "/admin/orders")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.AdminOrderController#create()", "POST", "/admin/orders")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        UmlUseCaseResponse.Actor admin = res.getActors().stream()
                .filter(a -> "Administrator".equals(a.getName())).findFirst().orElseThrow();
        UmlUseCaseResponse.Actor user = res.getActors().stream()
                .filter(a -> "Registered User".equals(a.getName())).findFirst().orElseThrow();
        assertThat(res.getRelations()).anyMatch(r -> "generalization".equals(r.getType())
                && admin.getId().equals(r.getFrom()) && user.getId().equals(r.getTo()));
        assertThat(res.getPlantUmlSyntax()).contains(admin.getId() + " --|> " + user.getId());
    }

    @Test
    @DisplayName("infers Admin from /admin path and User for non-admin endpoints")
    void actorInference() {
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("POST", "/admin/orders")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.AdminOrderController#create()", "POST", "/admin/orders")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .contains("Administrator", "Registered User");
    }

    @Test
    @DisplayName("a write endpoint is NOT mistaken for an Admin action")
    void writeDoesNotImplyAdmin() {
        stubGraph(
                List.of(endpoint("POST", "/api/products")),
                List.of(handles("com.app.ProductController#create()", "POST", "/api/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        // POST alone (no /admin path) belongs to the default authenticated User, never Admin.
        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .containsExactly("Registered User");
        assertThat(res.getWarnings()).anyMatch(w -> w.toLowerCase().contains("inferred"));
    }

    @Test
    @DisplayName("excludes infrastructure endpoints (actuator, swagger, health)")
    void excludesInfra() {
        stubGraph(
                List.of(endpoint("GET", "/actuator/health"),
                        endpoint("GET", "/swagger-ui/index.html"),
                        endpoint("GET", "/v3/api-docs"),
                        endpoint("GET", "/error"),
                        endpoint("GET", "/api/products")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .allMatch(n -> !n.toLowerCase().contains("actuator")
                        && !n.toLowerCase().contains("swagger")
                        && !n.toLowerCase().contains("api-docs")
                        && !n.toLowerCase().contains("error"));
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getDomain)
                .contains("Product");
    }

    @Test
    @DisplayName("excludes internal/debug plumbing endpoints")
    void excludesDebug() {
        stubGraph(
                List.of(endpoint("GET", "/api/debug/dump"),
                        endpoint("GET", "/internal/cache"),
                        endpoint("GET", "/api/products")),
                List.of(handles("com.app.DebugController#dump()", "GET", "/api/debug/dump"),
                        handles("com.app.InternalController#cache()", "GET", "/internal/cache"),
                        handles("com.app.ProductController#list()", "GET", "/api/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("View Products");
    }

    @Test
    @DisplayName("maps business auth endpoints to a Guest's Register/Log in, excludes token plumbing")
    void authMapping() {
        stubGraph(
                List.of(endpoint("POST", "/api/auth/register"),
                        endpoint("POST", "/api/auth/login"),
                        endpoint("POST", "/api/auth/refresh-token"),
                        endpoint("POST", "/api/auth/logout")),
                List.of(handles("com.app.AuthController#register()", "POST", "/api/auth/register"),
                        handles("com.app.AuthController#login()", "POST", "/api/auth/login"),
                        handles("com.app.AuthController#refresh()", "POST", "/api/auth/refresh-token"),
                        handles("com.app.AuthController#logout()", "POST", "/api/auth/logout")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactlyInAnyOrder("Register Account", "Log In");
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .noneMatch(n -> n.toLowerCase().contains("refresh") || n.toLowerCase().contains("logout"));
        // Pre-authentication goals belong to an anonymous Guest actor.
        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .contains("Guest");
    }

    @Test
    @DisplayName("auth endpoints without an /auth/ segment never produce zombie View Logins/Registers")
    void authWithoutAuthSegmentHasNoZombieCrud() {
        stubGraph(
                List.of(endpoint("POST", "/api/login"),
                        endpoint("POST", "/register"),
                        endpoint("POST", "/users/signin")),
                List.of(handles("com.app.LoginController#login()", "POST", "/api/login"),
                        handles("com.app.SignupController#register()", "POST", "/register"),
                        handles("com.app.UserController#signin()", "POST", "/users/signin")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .noneMatch(n -> {
                    String lower = n.toLowerCase();
                    return lower.startsWith("view login") || lower.startsWith("view register")
                            || lower.startsWith("manage login") || lower.startsWith("manage register")
                            || lower.contains("signin");
                });
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsOnly("Register Account", "Log In");
        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .contains("Guest");
    }

    @Test
    @DisplayName("produces valid PlantUML scaffolding")
    void plantUmlSyntax() {
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("POST", "/api/products")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.ProductController#create()", "POST", "/api/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "grouped");
        String puml = res.getPlantUmlSyntax();

        assertThat(puml).startsWith("@startuml");
        assertThat(puml).contains("left to right direction");
        assertThat(puml).contains("rectangle \"Shop System\"");
        assertThat(puml).contains("usecase \"");
        assertThat(puml).contains("actor \"");
        assertThat(puml).endsWith("@enduml");
    }

    @Test
    @DisplayName("Mermaid fallback uses flowchart TB with a single system boundary and oval nodes")
    void mermaidFallback() {
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("POST", "/admin/products")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.AdminProductController#create()", "POST", "/admin/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");
        String mer = res.getMermaidSyntax();

        assertThat(mer).startsWith("flowchart TB");
        assertThat(mer).contains("subgraph System[\"Shop System\"]");
        assertThat(mer).contains("direction TB");
        assertThat(mer).contains("([\"");
        // Direct actor->use-case associations (Mermaid ---).
        assertThat(mer).contains(" --- ");
        // No include/extend dotted arrows in the canonical model.
        assertThat(mer).doesNotContain("-.->");
    }

    @Test
    @DisplayName("Mermaid prints direction TB directly below subgraph and groups User before Admin")
    void mermaidStacksVerticallyAndGroupsByActor() {
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("POST", "/admin/orders")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.AdminOrderController#create()", "POST", "/admin/orders")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");
        String mer = res.getMermaidSyntax();

        // direction TB must be on the line immediately after the subgraph declaration.
        assertThat(mer).contains("subgraph System[\"Shop System\"]\n        direction TB");
        // User's goal (View Products) is declared before Admin's (Manage Orders) so lines do not cross.
        assertThat(mer.indexOf("UC_ViewProducts([")).isLessThan(mer.indexOf("UC_ManageOrders(["));
        assertThat(mer).contains("A_User --- UC_ViewProducts");
        assertThat(mer).contains("A_Admin --- UC_ManageOrders");
        assertThat(mer).doesNotContain("lane_");
    }

    @Test
    @DisplayName("a shared domain goal is declared once; Admin inherits it via generalization")
    void mermaidDeduplicatesSharedUseCase() {
        // A plain GET (User) and an /admin GET (Admin) resolve to the same read-only Product goal.
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("GET", "/admin/products")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.AdminProductController#adminList()", "GET", "/admin/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        // Single deduplicated goal node...
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("View Products");
        // ...reached by exactly ONE association (User's). Admin inherits via the generalization.
        assertThat(res.getRelations()).filteredOn(r -> "association".equals(r.getType())).hasSize(1);
        assertThat(res.getRelations()).anyMatch(r -> "generalization".equals(r.getType()));

        String mer = res.getMermaidSyntax();
        int declStart = mer.indexOf("UC_ViewProducts([");
        assertThat(declStart).isGreaterThanOrEqualTo(0);
        assertThat(mer.indexOf("UC_ViewProducts([", declStart + 1)).isEqualTo(-1);
        assertThat(mer).contains("A_User --- UC_ViewProducts");
        assertThat(mer).doesNotContain("A_Admin --- UC_ViewProducts");
        // Generalization edge is rendered as a clean line (no visible label) for SRS presentation.
        assertThat(mer).contains("A_Admin --> A_User");
        assertThat(mer).doesNotContain("|generalizes|");
    }

    @Test
    @DisplayName("output is deterministic across repeated calls")
    void deterministic() {
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("POST", "/api/products"),
                        endpoint("GET", "/api/orders")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.ProductController#create()", "POST", "/api/products"),
                        handles("com.app.OrderController#list()", "GET", "/api/orders")));

        UmlUseCaseResponse a = service.generateUmlUseCase(PROJECT_ID, "grouped");
        UmlUseCaseResponse b = service.generateUmlUseCase(PROJECT_ID, "grouped");

        assertThat(a.getPlantUmlSyntax()).isEqualTo(b.getPlantUmlSyntax());
        assertThat(a.getMermaidSyntax()).isEqualTo(b.getMermaidSyntax());
    }

    @Test
    @DisplayName("rejects an invalid mode with IllegalArgumentException")
    void invalidMode() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.generateUmlUseCase(PROJECT_ID, "bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("singularizes the domain and pluralizes the goal label (categories -> Category / Categories)")
    void singularizes() {
        stubGraph(
                List.of(endpoint("GET", "/api/categories")),
                List.of(handles("com.app.CategoryController#list()", "GET", "/api/categories")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "grouped");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("View Categories");
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getDomain)
                .containsExactly("Category");
    }
}
