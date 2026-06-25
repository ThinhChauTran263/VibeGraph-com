package com.vibegraph.diagram.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;
import com.vibegraph.diagram.service.impl.GenericRelationInferer;
import com.vibegraph.diagram.service.impl.GenericRelationInferer.EnrichedModel;

@DisplayName("GenericRelationInferer - generic shared-service include inference")
class GenericRelationInfererTest {

    private final GenericRelationInferer inferer = new GenericRelationInferer();

    private Actor actor(String id, String name) {
        return Actor.builder().id(id).name(name).source("s").confidence(0.8).build();
    }

    private UseCaseElement uc(String id, String name) {
        return UseCaseElement.builder().id(id).name(name).domain("d").level("business")
                .source("s").confidence(0.8).build();
    }

    private Relation assoc(String from, String to) {
        return Relation.builder().from(from).to(to).type("association").confidence(0.8).build();
    }

    @Test
    @DisplayName("promotes a business service shared by >= 2 use cases into an <<include>>")
    void sharedBusinessServiceBecomesInclude() {
        List<Actor> actors = List.of(actor("A_User", "Registered User"));
        List<UseCaseElement> useCases = List.of(uc("UC_ManageOrders", "Manage Orders"),
                uc("UC_ManageCarts", "Manage Carts"));
        List<Relation> relations = List.of(assoc("A_User", "UC_ManageOrders"),
                assoc("A_User", "UC_ManageCarts"));
        // Both order and cart controllers inject a PaymentService -> shared business capability.
        Map<String, Set<String>> services = Map.of(
                "UC_ManageOrders", Set.of("com.shop.PaymentService"),
                "UC_ManageCarts", Set.of("com.shop.PaymentService"));

        EnrichedModel out = inferer.enrich(actors, useCases, relations, services);

        assertThat(out.useCases()).extracting(UseCaseElement::getName).contains("Process Payment");
        assertThat(out.relations())
                .filteredOn(r -> "include".equals(r.getType()))
                .extracting(Relation::getTo)
                .containsOnly("UC_ProcessPayment");
        assertThat(out.relations())
                .filteredOn(r -> "include".equals(r.getType()))
                .extracting(Relation::getFrom)
                .containsExactlyInAnyOrder("UC_ManageOrders", "UC_ManageCarts");
        // Inferred include carries low confidence so the renderer can mark it heuristic.
        assertThat(out.useCases())
                .filteredOn(u -> "UC_ProcessPayment".equals(u.getId()))
                .allMatch(u -> u.getConfidence() < 0.6);
    }

    @Test
    @DisplayName("never auto-infers <<extend>>")
    void neverInfersExtend() {
        List<UseCaseElement> useCases = List.of(uc("UC_A", "Manage A"), uc("UC_B", "Manage B"));
        Map<String, Set<String>> services = Map.of(
                "UC_A", Set.of("com.x.ValidationService"),
                "UC_B", Set.of("com.x.ValidationService"));

        EnrichedModel out = inferer.enrich(List.of(), useCases, List.of(), services);

        assertThat(out.relations()).noneMatch(r -> "extend".equals(r.getType()));
    }

    @Test
    @DisplayName("does NOT promote infrastructure services (repository, mapper, client)")
    void ignoresInfrastructureServices() {
        List<UseCaseElement> useCases = List.of(uc("UC_A", "Manage A"), uc("UC_B", "Manage B"));
        Map<String, Set<String>> services = Map.of(
                "UC_A", Set.of("com.x.OrderRepository", "com.x.OrderMapper"),
                "UC_B", Set.of("com.x.OrderRepository", "com.x.OrderMapper"));

        EnrichedModel out = inferer.enrich(List.of(), useCases, List.of(), services);

        assertThat(out.useCases()).hasSize(2);
        assertThat(out.relations()).isEmpty();
    }

    @Test
    @DisplayName("does NOT promote a business service used by only ONE use case")
    void requiresAtLeastTwoSharers() {
        List<UseCaseElement> useCases = List.of(uc("UC_A", "Manage A"), uc("UC_B", "Manage B"));
        Map<String, Set<String>> services = Map.of(
                "UC_A", Set.of("com.x.PaymentService"));

        EnrichedModel out = inferer.enrich(List.of(), useCases, List.of(), services);

        assertThat(out.useCases()).hasSize(2);
        assertThat(out.relations()).isEmpty();
    }

    @Test
    @DisplayName("is a no-op when there are no injected services")
    void noOpWithoutServices() {
        List<UseCaseElement> useCases = List.of(uc("UC_A", "Manage A"));
        List<Relation> relations = List.of(assoc("A_User", "UC_A"));

        EnrichedModel out = inferer.enrich(List.of(actor("A_User", "User")), useCases, relations, Map.of());

        assertThat(out.useCases()).hasSize(1);
        assertThat(out.relations()).hasSize(1);
    }
}
