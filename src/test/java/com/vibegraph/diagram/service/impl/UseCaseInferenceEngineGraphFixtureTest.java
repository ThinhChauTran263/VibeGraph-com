package com.vibegraph.diagram.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;
import com.vibegraph.diagram.service.impl.UseCaseInferenceEngine.InferenceResult;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * B-M2 gate work (expensive cluster): fixture graphs that drive infer() through the
 * merge/disambiguate/INJECTS machinery (lines ~260-264, 373-414, 448-486) — the branches a
 * helper-level test cannot reach. Every assertion pins an observable property of the emitted
 * model, never merely "did not throw".
 */
class UseCaseInferenceEngineGraphFixtureTest {

    private final UseCaseInferenceEngine engine = new UseCaseInferenceEngine();

    private static NodeDto route(String routeId) {
        return NodeDto.builder()
                .id(routeId)
                .type("Route")
                .name(routeId)
                .fullName(routeId)
                .build();
    }

    private static EdgeDto edge(String source, String target, String type) {
        return EdgeDto.builder().source(source).target(target).type(type).build();
    }

    private static GraphDataResponse graph(List<NodeDto> nodes, List<EdgeDto> edges) {
        return GraphDataResponse.builder().nodes(nodes).edges(edges).build();
    }

    @Test
    @DisplayName("two controllers on one domain: services of both join the single goal (INJECTS)")
    void singleDomainCollectsServicesFromAllItsControllers() {
        // Two public mutating endpoints whose controllers beautify to the SAME domain "Order":
        // OrderController -> "Order", OrdersController -> singularize("Orders") -> "Order".
        // One domain aggregate -> one goal; its useCaseServices must be the union of both
        // controllers' INJECTS targets.
        GraphDataResponse g = graph(
                List.of(route("POST /api/orders"), route("POST /api/bulk-orders")),
                Arrays.asList(
                        edge("com.example.OrderController.create", "POST /api/orders", "HANDLES_ROUTE"),
                        edge("com.example.OrdersController.bulk", "POST /api/bulk-orders", "HANDLES_ROUTE"),
                        edge("com.example.OrderController", "com.example.OrderService", "INJECTS"),
                        edge("com.example.OrdersController", "com.example.BulkOrderService", "INJECTS"),
                        // blank owner -> collectInjects must skip, not fail
                        edge("", "com.example.OrphanService", "INJECTS"),
                        // null edge entry -> both collectors must skip nulls, not NPE
                        null));

        InferenceResult result = engine.infer(g, "business");

        List<UseCaseElement> manageOrders = result.useCases().stream()
                .filter(uc -> uc.getName().equals("Manage Orders"))
                .toList();
        assertThat(manageOrders)
                .as("the two same-scope 'Manage Orders' goals must collapse onto exactly one uc")
                .hasSize(1);
        assertThat(manageOrders.get(0).getId()).isEqualTo("UC_ManageOrders");

        // Services of the merged-away uc must have moved onto the survivor (union, sorted).
        Map<String, Set<String>> services = result.useCaseServices();
        assertThat(services).containsKey("UC_ManageOrders");
        assertThat(services.get("UC_ManageOrders"))
                .containsExactly("com.example.BulkOrderService", "com.example.OrderService");
        // The blank-owner INJECTS edge must not have leaked an entry.
        assertThat(services.values().stream().flatMap(Set::stream))
                .doesNotContain("com.example.OrphanService");
    }

    @Test
    @DisplayName("same-scope name collision from singularize lossiness merges onto the lowest id")
    void sameScopeNameCollisionMergesOntoLowestId() {
        // singularize is lossy: domains "Die" (from DieController) and "Dy" (from DyController)
        // are DIFFERENT domain keys but pluralize to the SAME label "Dies". Both goals are
        // non-admin "Manage Dies" -> mergeDuplicateNamedUseCases must collapse them onto the
        // lowest id, rewire the duplicate association away, and union the injected services.
        GraphDataResponse g = graph(
                List.of(route("POST /api/dies"), route("POST /api/dy")),
                List.of(
                        edge("com.example.DieController.create", "POST /api/dies", "HANDLES_ROUTE"),
                        edge("com.example.DyController.create", "POST /api/dy", "HANDLES_ROUTE"),
                        edge("com.example.DieController", "com.example.DieService", "INJECTS"),
                        edge("com.example.DyController", "com.example.DyService", "INJECTS")));

        InferenceResult result = engine.infer(g, "business");

        List<UseCaseElement> dies = result.useCases().stream()
                .filter(uc -> uc.getName().equals("Manage Dies"))
                .toList();
        assertThat(dies).as("the colliding pair must collapse onto exactly one uc").hasSize(1);
        assertThat(dies.get(0).getId())
                .as("lowest-sorted id survives the merge")
                .isEqualTo("UC_ManageDies");

        // The merged-away goal's services moved onto the survivor (union, sorted).
        assertThat(result.useCaseServices().get("UC_ManageDies"))
                .containsExactly("com.example.DieService", "com.example.DyService");

        // Both endpoints shared the User actor; after the merge the duplicated association
        // (same from, same surviving to) must be deduplicated to exactly one.
        String userId = result.actors().stream()
                .filter(a -> a.getName().equals("User")).findFirst().orElseThrow().getId();
        long userToDies = result.relations().stream()
                .filter(r -> "association".equals(r.getType())
                        && r.getFrom().equals(userId)
                        && r.getTo().equals("UC_ManageDies"))
                .count();
        assertThat(userToDies).isOne();
    }

    @Test
    @DisplayName("cross-scope name collision is disambiguated into (All)/(Own), not merged")
    void crossScopeCollisionIsDisambiguatedNotMerged() {
        // Admin surface: path /admin => Admin actor => admin-scoped "Manage Coupons".
        // Public surface: mutating public endpoint => User "Manage Coupons".
        // Same display name, DIFFERENT scopes -> both survive, qualified (All)/(Own).
        GraphDataResponse g = graph(
                List.of(route("GET /api/admin/coupons"), route("POST /api/coupons")),
                List.of());

        InferenceResult result = engine.infer(g, "business");

        List<UseCaseElement> all = result.useCases();
        assertThat(all).extracting(UseCaseElement::getName)
                .contains("Manage Coupons (All)", "Manage Coupons (Own)");
        UseCaseElement adminUc = all.stream()
                .filter(uc -> uc.getName().equals("Manage Coupons (All)")).findFirst().orElseThrow();
        UseCaseElement ownUc = all.stream()
                .filter(uc -> uc.getName().equals("Manage Coupons (Own)")).findFirst().orElseThrow();
        assertThat(adminUc.getId()).startsWith("UC_Admin");
        assertThat(ownUc.getId()).doesNotStartWith("UC_Admin");
        // No residual unqualified duplicate may survive disambiguation.
        assertThat(all).extracting(UseCaseElement::getName).doesNotContain("Manage Coupons");

        // Both actors exist, so the Admin--|>User generalization must be present exactly once.
        long generalizations = result.relations().stream()
                .filter(r -> "generalization".equals(r.getType()))
                .count();
        assertThat(generalizations).isOne();
    }

    @Test
    @DisplayName("auth endpoints become Guest goals; admin association inherited via generalization")
    void authEndpointsBecomeGuestGoals() {
        GraphDataResponse g = graph(
                List.of(
                        route("POST /api/auth/register"),
                        route("POST /api/auth/login"),
                        route("GET /api/admin/orders"),
                        route("GET /api/orders")),
                List.of());

        InferenceResult result = engine.infer(g, "business");

        assertThat(result.useCases()).extracting(UseCaseElement::getName)
                .contains("Register account", "Log in");
        assertThat(result.actors()).extracting(Actor::getName).contains("Guest", "Admin", "User");

        // Guest is wired to the two auth goals only.
        String guestId = result.actors().stream()
                .filter(a -> a.getName().equals("Guest")).findFirst().orElseThrow().getId();
        List<Relation> guestRelations = result.relations().stream()
                .filter(r -> r.getFrom().equals(guestId) && "association".equals(r.getType()))
                .toList();
        assertThat(guestRelations).hasSize(2);
    }

    @Test
    @DisplayName("malformed nodes/edges are skipped without killing inference")
    void malformedInputsAreSkippedNotFatal() {
        GraphDataResponse g = graph(
                List.of(
                        // Route node whose id carries no "METHOD path" shape and no props:
                        // toEndpoint must yield null, not throw.
                        route("unshaped-route-id"),
                        route("GET /api/valid")),
                Arrays.asList(
                        // HANDLES_ROUTE with null source -> controllerName(null) path
                        edge(null, "GET /api/valid", "HANDLES_ROUTE"),
                        // HANDLES_ROUTE whose source class name has no "Controller" suffix
                        edge("com.example.PlainHandler.list", "GET /api/valid", "HANDLES_ROUTE"),
                        // INJECTS with blank service -> collectInjects skips
                        edge("com.example.X", "", "INJECTS"),
                        // INJECTS with null service -> collectInjects skips
                        edge("com.example.Y", null, "INJECTS")));

        InferenceResult result = engine.infer(g, "business");

        // The one well-formed route still produces its goal (domain "Valid" pluralizes).
        assertThat(result.useCases()).extracting(UseCaseElement::getName).contains("View Valids");
        // Nothing leaked from the malformed INJECTS edges.
        assertThat(result.useCaseServices()).isEmpty();
    }
}
