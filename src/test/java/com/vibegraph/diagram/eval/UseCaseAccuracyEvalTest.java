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
