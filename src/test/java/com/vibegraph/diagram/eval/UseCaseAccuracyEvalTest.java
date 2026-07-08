package com.vibegraph.diagram.eval;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse;
import com.vibegraph.diagram.service.impl.BaLabelBeautifier;
import com.vibegraph.diagram.service.impl.GenericRelationInferer;
import com.vibegraph.diagram.service.impl.NoopUseCaseRefiner;
import com.vibegraph.diagram.service.impl.UmlUseCaseRenderer;
import com.vibegraph.diagram.service.impl.UseCaseDiagramServiceImpl;
import com.vibegraph.diagram.service.impl.UseCaseInferenceEngine;
import com.vibegraph.diagram.service.impl.UseCaseViewProjector;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.ProjectService;

/**
 * Eval harness (Requirement 1): measures the generated UML Use Case model against a hand-labelled
 * ground truth and reports precision / recall / F1 per dimension. Offline + deterministic.
 *
 * <p>The fixture is a trimmed fatc-grocery-store: public + admin product surfaces, a customer order
 * goal colliding with an admin order read, an admin read-only reporting controller, and an MVC view
 * page. The ground truth encodes the CORRECT model, so the printed report quantifies the remaining
 * known gap (admin read-only "Analytics" should read as a View goal, not Manage — deferred to the
 * semantic/LLM tier), while the assertions guard against regression of what is already correct.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Use Case accuracy eval harness")
class UseCaseAccuracyEvalTest {

    private static final String PROJECT_ID = "fatc";

    @Mock
    GraphService graphService;
    @Mock
    ProjectService projectService;

    private UseCaseDiagramServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UseCaseDiagramServiceImpl(graphService, projectService,
                new UseCaseInferenceEngine(), new UmlUseCaseRenderer(),
                new BaLabelBeautifier(), new GenericRelationInferer(),
                new UseCaseViewProjector(new UmlUseCaseRenderer()),
                new NoopUseCaseRefiner());
        lenient().when(projectService.getProject(PROJECT_ID))
                .thenReturn(ProjectResponse.builder().id(PROJECT_ID).name("Fatc").status("ANALYZED").build());
    }

    private NodeDto ep(String method, String path) {
        return NodeDto.builder().id(method + " " + path).type("APIEndpoint")
                .name(method + " " + path).fullName(method + " " + path)
                .properties(Map.of("httpMethod", method, "routePath", path)).build();
    }

    private NodeDto secured(String method, String path, String role) {
        return NodeDto.builder().id(method + " " + path).type("APIEndpoint")
                .name(method + " " + path).fullName(method + " " + path)
                .properties(Map.of("httpMethod", method, "routePath", path, "requiredRole", role)).build();
    }

    private NodeDto view(String method, String path) {
        return NodeDto.builder().id(method + " " + path).type("APIEndpoint")
                .name(method + " " + path).fullName(method + " " + path)
                .properties(Map.of("httpMethod", method, "routePath", path, "view", true)).build();
    }

    private EdgeDto handles(String fqcn, String method, String path) {
        return EdgeDto.builder().source(fqcn).target(method + " " + path).type("HANDLES_ROUTE").build();
    }

    // --- class-layer fixtures (projects without HTTP endpoints) -------------------------------

    private NodeDto svc(String fqcn, String simple) {
        return clazz(fqcn, simple, "Class", "SERVICE");
    }

    private NodeDto clazz(String fqcn, String simple, String type, String layer) {
        return NodeDto.builder().id(fqcn).type(type).name(simple).fullName(fqcn)
                .properties(Map.of("springLayer", layer)).build();
    }

    private NodeDto m(String methodFqcn, String simple) {
        return NodeDto.builder().id(methodFqcn).type("Method").name(simple).fullName(methodFqcn)
                .properties(Map.of("visibility", "public", "kind", "METHOD")).build();
    }

    private EdgeDto hasMethod(String owner, String methodFqcn) {
        return EdgeDto.builder().source(owner).target(methodFqcn).type("HAS_METHOD").build();
    }

    /** Run the service on a fixture graph, print the labelled accuracy report, and return it. */
    private UseCaseDiagramEvaluator.Report runAndReport(String title, GraphDataResponse graph,
            UseCaseDiagramEvaluator.ExpectedModel expected) {
        lenient().when(graphService.getFullGraph(PROJECT_ID)).thenReturn(graph);
        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");
        UseCaseDiagramEvaluator.Report report = UseCaseDiagramEvaluator.evaluate(res, expected);
        System.out.println("=== " + title + " ===");
        System.out.println(report.pretty());
        return report;
    }

    private void assertExact(UseCaseDiagramEvaluator.Report report) {
        assertThat(report.actors().f1()).as("actors F1").isEqualTo(1.0);
        assertThat(report.useCases().f1()).as("use cases F1").isEqualTo(1.0);
        assertThat(report.relations().f1()).as("relations F1").isEqualTo(1.0);
    }

    @Test
    @DisplayName("named security roles become distinct actors (Seller, Store Manager) — exact model")
    void namedRolesFixture() {
        GraphDataResponse graph = GraphDataResponse.builder()
                .nodes(List.of(
                        secured("GET", "/api/products", "SELLER"),
                        secured("POST", "/api/products", "SELLER"),
                        secured("GET", "/api/reports", "STORE_MANAGER")))
                .edges(List.of(
                        handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.ProductController#create()", "POST", "/api/products"),
                        handles("com.app.ReportController#list()", "GET", "/api/reports")))
                .build();
        UseCaseDiagramEvaluator.ExpectedModel expected = new UseCaseDiagramEvaluator.ExpectedModel(
                Set.of("Seller", "Store Manager"),
                Set.of("Manage Products", "View Reports"),
                Set.of("association|Seller|Manage Products", "association|Store Manager|View Reports"));
        assertExact(runAndReport("named roles", graph, expected));
    }

    @Test
    @DisplayName("service-layer-only project (no HTTP) — exact model from public methods")
    void serviceLayerFixture() {
        GraphDataResponse graph = GraphDataResponse.builder()
                .nodes(List.of(
                        svc("com.app.OrderService", "OrderService"),
                        m("com.app.OrderService.placeOrder()", "placeOrder"),
                        m("com.app.OrderService.cancelOrder()", "cancelOrder"),
                        m("com.app.OrderService.getOrder()", "getOrder"),
                        svc("com.app.CategoryService", "CategoryService"),
                        m("com.app.CategoryService.listCategories()", "listCategories"),
                        m("com.app.CategoryService.getCategory()", "getCategory")))
                .edges(List.of(
                        hasMethod("com.app.OrderService", "com.app.OrderService.placeOrder()"),
                        hasMethod("com.app.OrderService", "com.app.OrderService.cancelOrder()"),
                        hasMethod("com.app.OrderService", "com.app.OrderService.getOrder()"),
                        hasMethod("com.app.CategoryService", "com.app.CategoryService.listCategories()"),
                        hasMethod("com.app.CategoryService", "com.app.CategoryService.getCategory()")))
                .build();
        UseCaseDiagramEvaluator.ExpectedModel expected = new UseCaseDiagramEvaluator.ExpectedModel(
                Set.of("Registered User"),
                Set.of("Manage Orders", "View Categories"),
                Set.of("association|Registered User|Manage Orders",
                        "association|Registered User|View Categories"));
        assertExact(runAndReport("service layer", graph, expected));
    }

    @Test
    @DisplayName("entity-only project — Manage goals per entity, exact model")
    void entityOnlyFixture() {
        GraphDataResponse graph = GraphDataResponse.builder()
                .nodes(List.of(
                        clazz("com.app.Product", "Product", "DBModel", "ENTITY"),
                        clazz("com.app.Order", "Order", "DBModel", "ENTITY"),
                        clazz("com.app.Customer", "Customer", "DBModel", "ENTITY")))
                .edges(List.of())
                .build();
        UseCaseDiagramEvaluator.ExpectedModel expected = new UseCaseDiagramEvaluator.ExpectedModel(
                Set.of("Registered User"),
                Set.of("Manage Products", "Manage Orders", "Manage Customers"),
                Set.of("association|Registered User|Manage Products",
                        "association|Registered User|Manage Orders",
                        "association|Registered User|Manage Customers"));
        assertExact(runAndReport("entity only", graph, expected));
    }

    @Test
    @DisplayName("auth service contributes Guest register/login goals — exact model")
    void authServiceFixture() {
        GraphDataResponse graph = GraphDataResponse.builder()
                .nodes(List.of(
                        svc("com.app.AuthService", "AuthService"),
                        m("com.app.AuthService.register()", "register"),
                        m("com.app.AuthService.login()", "login")))
                .edges(List.of(
                        hasMethod("com.app.AuthService", "com.app.AuthService.register()"),
                        hasMethod("com.app.AuthService", "com.app.AuthService.login()")))
                .build();
        UseCaseDiagramEvaluator.ExpectedModel expected = new UseCaseDiagramEvaluator.ExpectedModel(
                Set.of("Guest"),
                Set.of("Register Account", "Log In"),
                Set.of("association|Guest|Register Account", "association|Guest|Log In"));
        assertExact(runAndReport("auth service", graph, expected));
    }

    @Test
    @DisplayName("interface + impl service collapses to one goal — exact model")
    void interfaceImplFixture() {
        GraphDataResponse graph = GraphDataResponse.builder()
                .nodes(List.of(
                        clazz("com.app.PaymentService", "PaymentService", "Interface", "NONE"),
                        m("com.app.PaymentService.pay()", "pay"),
                        m("com.app.PaymentService.refund()", "refund"),
                        svc("com.app.PaymentServiceImpl", "PaymentServiceImpl"),
                        m("com.app.PaymentServiceImpl.pay()", "pay"),
                        m("com.app.PaymentServiceImpl.refund()", "refund")))
                .edges(List.of(
                        hasMethod("com.app.PaymentService", "com.app.PaymentService.pay()"),
                        hasMethod("com.app.PaymentService", "com.app.PaymentService.refund()"),
                        hasMethod("com.app.PaymentServiceImpl", "com.app.PaymentServiceImpl.pay()"),
                        hasMethod("com.app.PaymentServiceImpl", "com.app.PaymentServiceImpl.refund()")))
                .build();
        UseCaseDiagramEvaluator.ExpectedModel expected = new UseCaseDiagramEvaluator.ExpectedModel(
                Set.of("Registered User"),
                Set.of("Manage Payments"),
                Set.of("association|Registered User|Manage Payments"));
        assertExact(runAndReport("interface + impl", graph, expected));
    }

    @Test
    @DisplayName("baseline accuracy on the trimmed fatc fixture meets the regression floor")
    void baselineAccuracy() {
        lenient().when(graphService.getFullGraph(PROJECT_ID)).thenReturn(GraphDataResponse.builder()
                .nodes(List.of(
                        ep("POST", "/api/auth/register"),
                        ep("POST", "/api/auth/login"),
                        ep("GET", "/api/products"),
                        secured("POST", "/api/admin/products", "ADMIN"),
                        ep("POST", "/api/orders/checkout"),
                        ep("GET", "/api/orders/admin/all"),
                        secured("GET", "/api/admin/analytics", "ADMIN"),
                        view("GET", "/checkout")))
                .edges(List.of(
                        handles("com.app.AuthController#register()", "POST", "/api/auth/register"),
                        handles("com.app.AuthController#login()", "POST", "/api/auth/login"),
                        handles("com.app.ProductController#list()", "GET", "/api/products"),
                        handles("com.app.AdminProductController#create()", "POST", "/api/admin/products"),
                        handles("com.app.OrderController#checkout()", "POST", "/api/orders/checkout"),
                        handles("com.app.OrderController#adminAll()", "GET", "/api/orders/admin/all"),
                        handles("com.app.AdminAnalyticsController#stats()", "GET", "/api/admin/analytics"),
                        handles("com.app.HomeController#checkout()", "GET", "/checkout")))
                .build());

        UmlUseCaseResponse res = service.generateUmlUseCase(PROJECT_ID, "detailed");

        // Ground truth: the CORRECT business model for this fixture.
        UseCaseDiagramEvaluator.ExpectedModel expected = new UseCaseDiagramEvaluator.ExpectedModel(
                Set.of("Guest", "Registered User", "Administrator"),
                Set.of("Register Account", "Log In", "View Products", "Manage Products",
                        "Manage Orders (Own)", "Manage Orders (All)", "View Analytics"),
                Set.of(
                        "association|Guest|Register Account",
                        "association|Guest|Log In",
                        "association|Registered User|View Products",
                        "association|Administrator|Manage Products",
                        "association|Registered User|Manage Orders (Own)",
                        "association|Administrator|Manage Orders (All)",
                        "association|Administrator|View Analytics",
                        "generalization|Administrator|Registered User"));

        UseCaseDiagramEvaluator.Report report = UseCaseDiagramEvaluator.evaluate(res, expected);
        System.out.println("=== Use Case accuracy report (trimmed fatc) ===");
        System.out.println(report.pretty());

        // The view page must never leak in as a goal.
        assertThat(res.getUseCases()).extracting(UmlUseCaseResponse.UseCaseElement::getName)
                .noneMatch(n -> n.toLowerCase().contains("checkout"));
        // Actors are inferred reliably (fact-backed roles + path) -> exact.
        assertThat(report.actors().f1()).isEqualTo(1.0);
        // After the reporting-domain rule (admin read-only "Analytics" => "View Analytics"), the
        // trimmed fixture is modelled exactly. This is the regression guard going forward.
        assertThat(report.useCases().f1()).isEqualTo(1.0);
        assertThat(report.relations().f1()).isEqualTo(1.0);
    }
}
