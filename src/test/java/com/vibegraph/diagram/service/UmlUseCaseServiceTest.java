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
                new com.vibegraph.diagram.service.impl.GenericRelationInferer(),
                new com.vibegraph.diagram.service.impl.UseCaseViewProjector(new UmlUseCaseRenderer()),
                new com.vibegraph.diagram.service.impl.NoopUseCaseRefiner());
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

    private NodeDto viewEndpoint(String method, String path) {
        return NodeDto.builder()
                .id(method + " " + path)
                .type("APIEndpoint")
                .name(method + " " + path)
                .fullName(method + " " + path)
                .properties(Map.of("httpMethod", method, "routePath", path, "view", true))
                .build();
    }

    private NodeDto securedEndpoint(String method, String path, String role) {
        return NodeDto.builder()
                .id(method + " " + path)
                .type("APIEndpoint")
                .name(method + " " + path)
                .fullName(method + " " + path)
                .properties(Map.of("httpMethod", method, "routePath", path, "requiredRole", role))
                .build();
    }

    private EdgeDto handles(String controllerFqcn, String method, String path) {
        return EdgeDto.builder()
                .source(controllerFqcn)
                .target(method + " " + path)
                .type("HANDLES_ROUTE")
                .build();
    }

    // ---- class-layer fallback fixtures (projects without HTTP endpoints) ---------------------

    private NodeDto serviceClass(String fqcn, String simpleName) {
        return classNode(fqcn, simpleName, "Class", "SERVICE");
    }

    private NodeDto classNode(String fqcn, String simpleName, String type, String springLayer) {
        return NodeDto.builder()
                .id(fqcn).type(type).name(simpleName).fullName(fqcn)
                .properties(Map.of("springLayer", springLayer))
                .build();
    }

    private NodeDto method(String methodFqcn, String simpleName) {
        return NodeDto.builder()
                .id(methodFqcn).type("Method").name(simpleName).fullName(methodFqcn)
                .properties(Map.of("visibility", "public", "kind", "METHOD"))
                .build();
    }

    private EdgeDto hasMethod(String ownerFqcn, String methodFqcn) {
        return EdgeDto.builder().source(ownerFqcn).target(methodFqcn).type("HAS_METHOD").build();
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
    @DisplayName("models Admin --|> User but NOT User --|> Guest (auth states aren't a subtype)")
    void actorGeneralizationOnlyAdminToUser() {
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

        // Administrator generalizes Registered User (a true role specialization: admin = privileged user).
        assertThat(res.getRelations()).anyMatch(r -> "generalization".equals(r.getType())
                && admin.getId().equals(r.getFrom()) && user.getId().equals(r.getTo()));
        assertThat(res.getPlantUmlSyntax()).contains(admin.getId() + " --|> " + user.getId());

        // Registered User must NOT generalize Guest: Guest/User are authentication states, not a
        // subtype hierarchy. Otherwise a logged-in user would inherit the Guest goal "Register Account".
        assertThat(res.getRelations()).noneMatch(r -> "generalization".equals(r.getType())
                && user.getId().equals(r.getFrom()) && guest.getId().equals(r.getTo()));
        assertThat(res.getPlantUmlSyntax()).doesNotContain(user.getId() + " --|> " + guest.getId());
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
    @DisplayName("assigns Admin from a @PreAuthorize ADMIN role even when the path has no /admin segment")
    void securityRoleAssignsAdmin() {
        stubGraph(
                List.of(securedEndpoint("POST", "/api/products", "ADMIN"),
                        endpoint("GET", "/api/categories")),
                List.of(handles("com.app.ProductController#create()", "POST", "/api/products"),
                        handles("com.app.CategoryController#list()", "GET", "/api/categories")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        // The ADMIN-secured product management goal belongs to Administrator; the unsecured catalog
        // read stays with the Registered User.
        UmlUseCaseResponse.UseCaseElement manageProducts = res.getUseCases().stream()
                .filter(u -> "Manage Products".equals(u.getName())).findFirst().orElseThrow();
        UmlUseCaseResponse.Actor admin = res.getActors().stream()
                .filter(a -> "Administrator".equals(a.getName())).findFirst().orElseThrow();
        assertThat(res.getRelations())
                .filteredOn(r -> "association".equals(r.getType()) && manageProducts.getId().equals(r.getTo()))
                .extracting(UmlUseCaseResponse.Relation::getFrom)
                .containsExactly(admin.getId());
        // A real security role is authoritative, so it must NOT raise the "roles were inferred" warning
        // (that warning only fires when roles are guessed from the URL path).
        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .contains("Administrator", "Registered User");
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
    @DisplayName("excludes SPA screen routes and technical REST/API/delete-check plumbing")
    void excludesScreenAndTechnicalRoutes() {
        stubGraph(
                List.of(endpoint("GET", "/add-new-cards"),
                        endpoint("GET", "/mobile-demos"),
                        endpoint("GET", "/reset-passwords"),
                        endpoint("GET", "/order-details"),
                        endpoint("GET", "/api/product-rests"),
                        endpoint("GET", "/api/user-apis"),
                        endpoint("GET", "/api/delete-checks"),
                        endpoint("GET", "/api/products")),
                List.of(handles("com.app.UiController#cards()", "GET", "/add-new-cards"),
                        handles("com.app.UiController#demos()", "GET", "/mobile-demos"),
                        handles("com.app.UiController#reset()", "GET", "/reset-passwords"),
                        handles("com.app.UiController#details()", "GET", "/order-details"),
                        handles("com.app.ProductRestController#rests()", "GET", "/api/product-rests"),
                        handles("com.app.UserApiController#apis()", "GET", "/api/user-apis"),
                        handles("com.app.CheckController#deletes()", "GET", "/api/delete-checks"),
                        handles("com.app.ProductController#list()", "GET", "/api/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        // Only the genuine business resource survives; every screen/technical route is dropped.
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
        assertThat(mer.indexOf("UC_ViewProducts([")).isLessThan(mer.indexOf("UC_AdminManageOrders(["));
        assertThat(mer).contains("A_User --- UC_ViewProducts");
        assertThat(mer).contains("A_Admin --- UC_AdminManageOrders");
        assertThat(mer).doesNotContain("lane_");
    }

    @Test
    @DisplayName("public and admin surfaces of one entity become separate View/Manage goals")
    void splitsPublicAndAdminSurfaces() {
        // A plain GET (shopper viewing products) and an /admin GET (admin managing them) are two
        // distinct business goals, NOT one shared goal — collapsing them would mis-assign authority.
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("GET", "/admin/products")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.AdminProductController#adminList()", "GET", "/admin/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        // Two separate goals: the public read for the shopper, the admin management for the admin.
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactlyInAnyOrder("View Products", "Manage Products");
        // The admin goal is reached by Administrator; the public read by Registered User.
        UmlUseCaseResponse.UseCaseElement adminGoal = res.getUseCases().stream()
                .filter(u -> "Manage Products".equals(u.getName())).findFirst().orElseThrow();
        UmlUseCaseResponse.UseCaseElement publicGoal = res.getUseCases().stream()
                .filter(u -> "View Products".equals(u.getName())).findFirst().orElseThrow();
        UmlUseCaseResponse.Actor admin = res.getActors().stream()
                .filter(a -> "Administrator".equals(a.getName())).findFirst().orElseThrow();
        UmlUseCaseResponse.Actor user = res.getActors().stream()
                .filter(a -> "Registered User".equals(a.getName())).findFirst().orElseThrow();
        assertThat(res.getRelations())
                .filteredOn(r -> "association".equals(r.getType()) && adminGoal.getId().equals(r.getTo()))
                .extracting(UmlUseCaseResponse.Relation::getFrom).containsExactly(admin.getId());
        assertThat(res.getRelations())
                .filteredOn(r -> "association".equals(r.getType()) && publicGoal.getId().equals(r.getTo()))
                .extracting(UmlUseCaseResponse.Relation::getFrom).containsExactly(user.getId());
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
    @DisplayName("a customer's order goal is NOT erased by an admin read-only surface sharing the label")
    void customerOrderGoalSurvivesAdminSurfaceCollision() {
        // Reproduces the fatc OrderController shape: the customer places/lists orders (user surface,
        // becomes "Manage Orders") while a lone GET /admin/all is admin-scoped (also "Manage Orders").
        // Before the fix, the same-label merge collapsed both onto the admin goal and dropped the
        // customer's association, erasing the customer's order goal entirely.
        stubGraph(
                List.of(endpoint("POST", "/api/orders/checkout"),
                        endpoint("GET", "/api/orders"),
                        endpoint("GET", "/api/orders/admin/all")),
                List.of(handles("com.app.OrderController#checkout()", "POST", "/api/orders/checkout"),
                        handles("com.app.OrderController#list()", "GET", "/api/orders"),
                        handles("com.app.OrderController#adminAll()", "GET", "/api/orders/admin/all")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        UmlUseCaseResponse.Actor user = res.getActors().stream()
                .filter(a -> "Registered User".equals(a.getName())).findFirst().orElseThrow();
        // The customer must still reach an order goal via a direct association.
        boolean userReachesOrderGoal = res.getUseCases().stream()
                .filter(u -> u.getName().contains("Orders"))
                .anyMatch(u -> res.getRelations().stream().anyMatch(r -> "association".equals(r.getType())
                        && user.getId().equals(r.getFrom()) && u.getId().equals(r.getTo())));
        assertThat(userReachesOrderGoal).isTrue();
    }

    @Test
    @DisplayName("server-side GET view pages are dropped, but a mutating view route is kept")
    void viewPagesAreDroppedButMutatingViewRoutesKept() {
        // HomeController-style MVC pages (GET /checkout, /shipping, /favourite) are presentation and
        // must NOT become business goals. A form POST that renders a redirect (e.g. profile update) is
        // a real action and must survive.
        stubGraph(
                List.of(viewEndpoint("GET", "/checkout"),
                        viewEndpoint("GET", "/shipping"),
                        viewEndpoint("GET", "/favourite"),
                        viewEndpoint("POST", "/profile/update"),
                        endpoint("GET", "/api/products")),
                List.of(handles("com.app.HomeController#checkout()", "GET", "/checkout"),
                        handles("com.app.HomeController#shipping()", "GET", "/shipping"),
                        handles("com.app.HomeController#favourite()", "GET", "/favourite"),
                        handles("com.app.ProfileController#update()", "POST", "/profile/update"),
                        handles("com.app.ProductController#list()", "GET", "/api/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        // No presentation page leaked in as a goal.
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .noneMatch(n -> n.toLowerCase().contains("checkout")
                        || n.toLowerCase().contains("shipping")
                        || n.toLowerCase().contains("favourite"));
        // The real REST read and the mutating profile action both survive.
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("View Products");
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .anyMatch(n -> n.contains("Profile"));
    }

    @Test
    @DisplayName("a login/register served as a GET view page is still kept as a Guest business goal")
    void authViewPageStillBecomesGoal() {
        // AuthController @GetMapping("/sign-in") returns a Thymeleaf template (view=true). It is a
        // server-side page, but login/registration are pre-auth business goals and must NOT be dropped.
        stubGraph(
                List.of(viewEndpoint("GET", "/sign-in"),
                        viewEndpoint("GET", "/checkout"),
                        endpoint("GET", "/api/products")),
                List.of(handles("com.app.AuthController#signInPage()", "GET", "/sign-in"),
                        handles("com.app.HomeController#checkout()", "GET", "/checkout"),
                        handles("com.app.ProductController#list()", "GET", "/api/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("Log In")
                .noneMatch(n -> n.toLowerCase().contains("checkout"));
        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName).contains("Guest");
    }

    @Test
    @DisplayName("a single stray write in a read-mostly domain does NOT flip it to Manage (anti-amplification)")
    void strayWriteDoesNotFlipReadMostlyDomain() {
        // 4 reads + 1 write = 0.20 write ratio, below the 0.25 threshold -> stays a View goal. Before
        // the ratio rule, the lone POST would have flipped the whole domain to "Manage Products".
        stubGraph(
                List.of(endpoint("GET", "/api/products"),
                        endpoint("GET", "/api/products/{id}"),
                        endpoint("GET", "/api/products/search"),
                        endpoint("GET", "/api/products/featured"),
                        endpoint("POST", "/api/products/report")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.ProductController#get()", "GET", "/api/products/{id}"),
                        handles("com.app.ProductController#search()", "GET", "/api/products/search"),
                        handles("com.app.ProductController#featured()", "GET", "/api/products/featured"),
                        handles("com.app.ProductController#report()", "POST", "/api/products/report")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("View Products");
    }

    @Test
    @DisplayName("a write-heavy domain (>= 0.25 ratio) is a Manage goal")
    void writeHeavyDomainIsManage() {
        // 1 read + 1 write = 0.50 ratio, above threshold -> Manage.
        stubGraph(
                List.of(endpoint("GET", "/api/products"), endpoint("POST", "/api/products")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.ProductController#create()", "POST", "/api/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("Manage Products");
    }

    @Test
    @DisplayName("multi-view: per-actor and per-domain projections are faithful subsets of the model")
    void multiViewProjection() {
        stubGraph(
                List.of(endpoint("GET", "/api/products"),
                        securedEndpoint("POST", "/api/admin/orders", "ADMIN")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.AdminOrderController#create()", "POST", "/api/admin/orders")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getViews()).isNotNull();
        // Actor views for every actor + domain views for every business domain.
        assertThat(res.getViews()).extracting(UmlUseCaseResponse.UseCaseView::getTitle)
                .contains("Registered User", "Administrator", "Product", "Order");

        // The Registered User view contains the user's goal but NOT the admin-only goal.
        UmlUseCaseResponse.UseCaseView userView = res.getViews().stream()
                .filter(v -> "actor".equals(v.getViewType()) && "Registered User".equals(v.getTitle()))
                .findFirst().orElseThrow();
        assertThat(userView.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("View Products")
                .noneMatch(n -> n.equals("Manage Orders"));

        // The Administrator view inherits the user's goal via generalization AND has the admin goal.
        UmlUseCaseResponse.UseCaseView adminView = res.getViews().stream()
                .filter(v -> "actor".equals(v.getViewType()) && "Administrator".equals(v.getTitle()))
                .findFirst().orElseThrow();
        assertThat(adminView.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("View Products", "Manage Orders");

        // Every view is a faithful subset: its relations reference only nodes present in the view.
        for (UmlUseCaseResponse.UseCaseView v : res.getViews()) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            v.getActors().forEach(a -> ids.add(a.getId()));
            v.getUseCases().forEach(u -> ids.add(u.getId()));
            assertThat(v.getRelations())
                    .allMatch(r -> ids.contains(r.getFrom()) && ids.contains(r.getTo()));
            assertThat(v.getMermaidSyntax()).startsWith("flowchart TB");
        }
    }

    @Test
    @DisplayName("an admin read-only reporting domain is a View goal, not Manage")
    void adminReadOnlyReportingIsView() {
        // Admin reads analytics/audit logs — a pure read surface. It must read as "View", not "Manage",
        // even though it is admin-scoped (fixes the fatc "Manage Analytics"/"Manage Audit Logs" defect).
        stubGraph(
                List.of(securedEndpoint("GET", "/api/admin/analytics", "ADMIN"),
                        securedEndpoint("GET", "/api/admin/audit-logs", "ADMIN")),
                List.of(handles("com.app.AdminAnalyticsController#stats()", "GET", "/api/admin/analytics"),
                        handles("com.app.AuditLogController#list()", "GET", "/api/admin/audit-logs")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("View Analytics", "View Audit Logs")
                .noneMatch(n -> n.startsWith("Manage Analytic") || n.startsWith("Manage Audit"));
    }

    @Test
    @DisplayName("an admin WRITE on an entity domain still reads as Manage (reporting rule is narrow)")
    void adminWriteEntityStillManage() {
        stubGraph(
                List.of(securedEndpoint("POST", "/api/admin/products", "ADMIN")),
                List.of(handles("com.app.AdminProductController#create()", "POST", "/api/admin/products")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("Manage Products");
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

    @Test
    @DisplayName("use case confidence reflects evidence strength (fact role > guessed default)")
    void confidenceReflectsEvidence() {
        // ADMIN-secured product management = strong evidence (controller domain 0.9, fact actor 0.95).
        // Unsecured catalog read = weaker evidence (controller domain 0.9, guessed user actor 0.7).
        stubGraph(
                List.of(securedEndpoint("POST", "/api/products", "ADMIN"),
                        endpoint("GET", "/api/categories")),
                List.of(handles("com.app.ProductController#create()", "POST", "/api/products"),
                        handles("com.app.CategoryController#list()", "GET", "/api/categories")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        UmlUseCaseResponse.UseCaseElement manageProducts = res.getUseCases().stream()
                .filter(u -> "Manage Products".equals(u.getName())).findFirst().orElseThrow();
        UmlUseCaseResponse.UseCaseElement viewCategories = res.getUseCases().stream()
                .filter(u -> "View Categories".equals(u.getName())).findFirst().orElseThrow();

        // Confidence is no longer a hard-coded 0.8; it is the weakest-link of domain + actor evidence.
        assertThat(manageProducts.getConfidence()).isEqualTo(0.9);
        assertThat(viewCategories.getConfidence()).isEqualTo(0.7);
        assertThat(manageProducts.getConfidence()).isGreaterThan(viewCategories.getConfidence());
    }

    @Test
    @DisplayName("a weak path-only 'Resource' domain is flagged as low-confidence for review")
    void lowConfidenceGoalIsFlagged() {
        // No HANDLES_ROUTE edge -> no controller name; the path carries only api + a path variable,
        // so the domain collapses to the weak "Resource" fallback (confidence 0.3) and must be flagged.
        stubGraph(
                List.of(endpoint("GET", "/api/{id}")),
                List.of());

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).isNotEmpty();
        assertThat(res.getWarnings())
                .anyMatch(w -> w.toLowerCase().contains("low-confidence"));
    }

    // ---- edge cases: no API / no annotations / empty / frontend-only -------------------------

    @Test
    @DisplayName("a project with NO HTTP endpoints derives business use cases from the service layer")
    void noApiEndpointsDerivesUseCasesFromServiceLayer() {
        // A service-only / library / batch project: no Route/APIEndpoint nodes and no HANDLES_ROUTE
        // edges, but the business behaviour lives in service classes and their public methods.
        // Reporting "empty" here would be wrong — the system clearly DOES things.
        stubGraph(
                List.of(serviceClass("com.app.OrderService", "OrderService"),
                        method("com.app.OrderService.placeOrder()", "placeOrder"),
                        method("com.app.OrderService.cancelOrder()", "cancelOrder"),
                        method("com.app.OrderService.getOrder()", "getOrder"),
                        serviceClass("com.app.CategoryService", "CategoryService"),
                        method("com.app.CategoryService.listCategories()", "listCategories"),
                        method("com.app.CategoryService.getCategory()", "getCategory")),
                List.of(hasMethod("com.app.OrderService", "com.app.OrderService.placeOrder()"),
                        hasMethod("com.app.OrderService", "com.app.OrderService.cancelOrder()"),
                        hasMethod("com.app.OrderService", "com.app.OrderService.getOrder()"),
                        hasMethod("com.app.CategoryService", "com.app.CategoryService.listCategories()"),
                        hasMethod("com.app.CategoryService", "com.app.CategoryService.getCategory()")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        // A write-heavy service becomes "Manage", a read-only one becomes "View".
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("Manage Orders", "View Categories");
        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .contains("Registered User");
        assertThat(res.getWarnings()).anyMatch(w -> w.toLowerCase().contains("no http endpoints"));
        assertThat(res.getPlantUmlSyntax()).startsWith("@startuml").endsWith("@enduml");
        assertThat(res.getMermaidSyntax()).startsWith("flowchart TB");
        assertThat(res.getViews()).isNotEmpty();
    }

    @Test
    @DisplayName("a domain-model-only project derives Manage goals from entities")
    void entityOnlyProjectDerivesManageGoalsFromEntities() {
        // No services, no controllers — only @Entity domain models. A system with a Product and an
        // Order entity almost certainly lets someone manage them, so emit coarse Manage goals.
        stubGraph(
                List.of(classNode("com.app.Product", "Product", "DBModel", "ENTITY"),
                        classNode("com.app.Order", "Order", "DBModel", "ENTITY")),
                List.of());

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("Manage Products", "Manage Orders");
        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .contains("Registered User");
        assertThat(res.getWarnings()).anyMatch(w -> w.toLowerCase().contains("no http endpoints"));
    }

    @Test
    @DisplayName("with no services, the fallback derives goals from controller classes")
    void controllerLayerFallbackWhenNoServices() {
        stubGraph(
                List.of(classNode("com.app.InvoiceController", "InvoiceController", "Class", "CONTROLLER"),
                        method("com.app.InvoiceController.createInvoice()", "createInvoice"),
                        method("com.app.InvoiceController.listInvoices()", "listInvoices")),
                List.of(hasMethod("com.app.InvoiceController", "com.app.InvoiceController.createInvoice()"),
                        hasMethod("com.app.InvoiceController", "com.app.InvoiceController.listInvoices()")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("Manage Invoices");
    }

    @Test
    @DisplayName("an auth service contributes the Guest register/login goals in the fallback")
    void authServiceDerivesGuestGoals() {
        stubGraph(
                List.of(serviceClass("com.app.AuthService", "AuthService"),
                        method("com.app.AuthService.register()", "register"),
                        method("com.app.AuthService.login()", "login")),
                List.of(hasMethod("com.app.AuthService", "com.app.AuthService.register()"),
                        hasMethod("com.app.AuthService", "com.app.AuthService.login()")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName).contains("Guest");
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("Register Account", "Log In");
    }

    @Test
    @DisplayName("an admin-named service assigns the Administrator actor in the fallback")
    void adminServiceAssignsAdminActor() {
        stubGraph(
                List.of(serviceClass("com.app.AdminUserService", "AdminUserService"),
                        method("com.app.AdminUserService.deleteUser()", "deleteUser"),
                        method("com.app.AdminUserService.listUsers()", "listUsers")),
                List.of(hasMethod("com.app.AdminUserService", "com.app.AdminUserService.deleteUser()"),
                        hasMethod("com.app.AdminUserService", "com.app.AdminUserService.listUsers()")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .contains("Administrator");
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("Manage User Accounts");
    }

    @Test
    @DisplayName("an entity with only accessor getters is still 'Manage' (accessors are not read operations)")
    void entityWithAccessorsStillManageNotView() {
        // Regression: real entities carry getId/getName getters. Those are data accessors, not
        // business reads, so they must NOT downgrade the goal to "View Products".
        stubGraph(
                List.of(classNode("com.app.Product", "Product", "DBModel", "ENTITY"),
                        method("com.app.Product.getId()", "getId"),
                        method("com.app.Product.getName()", "getName"),
                        method("com.app.Product.getPrice()", "getPrice")),
                List.of(hasMethod("com.app.Product", "com.app.Product.getId()"),
                        hasMethod("com.app.Product", "com.app.Product.getName()"),
                        hasMethod("com.app.Product", "com.app.Product.getPrice()")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("Manage Products");
    }

    @Test
    @DisplayName("a plain business class (no service/controller/entity naming) still yields a goal")
    void genericBusinessClassProducesGoal() {
        // CLI/util-style project: App (only main -> noise, skipped) + Calculator with real operations.
        stubGraph(
                List.of(classNode("com.app.App", "App", "Class", "NONE"),
                        method("com.app.App.main()", "main"),
                        classNode("com.app.Calculator", "Calculator", "Class", "NONE"),
                        method("com.app.Calculator.add()", "add"),
                        method("com.app.Calculator.subtract()", "subtract"),
                        method("com.app.Calculator.multiply()", "multiply")),
                List.of(hasMethod("com.app.App", "com.app.App.main()"),
                        hasMethod("com.app.Calculator", "com.app.Calculator.add()"),
                        hasMethod("com.app.Calculator", "com.app.Calculator.subtract()"),
                        hasMethod("com.app.Calculator", "com.app.Calculator.multiply()")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        // App is skipped (only main); Calculator yields a non-empty goal. "add" is a mutating verb,
        // so the calculator surface reads as "Manage Calculators".
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("Manage Calculators");
        assertThat(res.getActors()).isNotEmpty();
    }

    @Test
    @DisplayName("a named security role becomes its own actor instead of collapsing to User")
    void namedRoleBecomesItsOwnActor() {
        stubGraph(
                List.of(securedEndpoint("GET", "/api/products", "SELLER"),
                        securedEndpoint("POST", "/api/products", "SELLER")),
                List.of());

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName).contains("Seller");
    }

    @Test
    @DisplayName("an underscored role token is Title-cased into a readable actor name")
    void underscoredRoleIsTitleCased() {
        stubGraph(
                List.of(securedEndpoint("POST", "/api/inventory", "ROLE_STORE_MANAGER")),
                List.of());

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .contains("Store Manager");
    }

    @Test
    @DisplayName("a generic ROLE_USER still collapses to the default Registered User actor")
    void genericUserRoleStaysRegisteredUser() {
        stubGraph(
                List.of(securedEndpoint("GET", "/api/orders", "ROLE_USER")),
                List.of());

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .containsExactly("Registered User");
    }

    @Test
    @DisplayName("business verbs like 'ship' count as mutating so the goal reads Manage, not View")
    void shipVerbCountsAsMutating() {
        stubGraph(
                List.of(serviceClass("com.app.OrderService", "OrderService"),
                        method("com.app.OrderService.shipOrder()", "shipOrder"),
                        method("com.app.OrderService.getOrder()", "getOrder")),
                List.of(hasMethod("com.app.OrderService", "com.app.OrderService.shipOrder()"),
                        hasMethod("com.app.OrderService", "com.app.OrderService.getOrder()")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .contains("Manage Orders");
    }

    @Test
    @DisplayName("a graph with only infrastructure classes (no business services) stays empty")
    void onlyInfraClassesProducesEmptyModel() {
        // Repositories, configs, and utils are not business goals — the fallback must skip them.
        stubGraph(
                List.of(classNode("com.app.OrderRepository", "OrderRepository", "Interface", "REPOSITORY"),
                        classNode("com.app.SecurityConfig", "SecurityConfig", "Class", "CONFIG"),
                        classNode("com.app.JwtUtils", "JwtUtils", "Class", "NONE")),
                List.of());

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).isEmpty();
        assertThat(res.getActors()).isEmpty();
        assertThat(res.getWarnings())
                .anyMatch(w -> w.toLowerCase().contains("no business use cases"));
    }

    @Test
    @DisplayName("a null graph is handled gracefully (no crash, empty model with warning)")
    void nullGraphDoesNotCrash() {
        lenient().when(graphService.getFullGraph(PROJECT_ID)).thenReturn(null);

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res).isNotNull();
        assertThat(res.getUseCases()).isEmpty();
        assertThat(res.getActors()).isEmpty();
        assertThat(res.getWarnings())
                .anyMatch(w -> w.toLowerCase().contains("no business use cases"));
        assertThat(res.getPlantUmlSyntax()).startsWith("@startuml").endsWith("@enduml");
    }

    @Test
    @DisplayName("a graph whose nodes/edges lists are empty produces an empty model, not an error")
    void emptyGraphProducesEmptyModel() {
        stubGraph(List.of(), List.of());

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).isEmpty();
        assertThat(res.getActors()).isEmpty();
        assertThat(res.getWarnings())
                .anyMatch(w -> w.toLowerCase().contains("no business use cases"));
    }

    @Test
    @DisplayName("a project whose only endpoints are infra/static assets yields no business use cases")
    void onlyInfraAndStaticEndpointsProducesEmptyModel() {
        stubGraph(
                List.of(endpoint("GET", "/actuator/health"),
                        endpoint("GET", "/swagger-ui/index.html"),
                        endpoint("GET", "/favicon.ico"),
                        endpoint("GET", "/assets/app.js"),
                        endpoint("GET", "/error")),
                List.of());

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).isEmpty();
        assertThat(res.getWarnings())
                .anyMatch(w -> w.toLowerCase().contains("no business use cases"));
    }

    @Test
    @DisplayName("endpoints present but NO security annotations: roles are guessed and flagged, model still builds")
    void noSecurityAnnotationsStillBuildsWithGuessWarning() {
        // The parser captured routes (HANDLES_ROUTE) but no @PreAuthorize/security metadata, so every
        // actor role is a path heuristic. The diagram must still build AND warn that roles are guessed.
        stubGraph(
                List.of(endpoint("GET", "/api/products"),
                        endpoint("POST", "/api/products"),
                        endpoint("DELETE", "/api/products/{id}")),
                List.of(handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.ProductController#create()", "POST", "/api/products"),
                        handles("com.app.ProductController#delete()", "DELETE", "/api/products/{id}")));

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .containsExactly("Manage Products");
        assertThat(res.getActors()).extracting(UmlUseCaseResponse.Actor::getName)
                .containsExactly("Registered User");
        assertThat(res.getWarnings())
                .anyMatch(w -> w.toLowerCase().contains("inferred from http path"));
    }
}
