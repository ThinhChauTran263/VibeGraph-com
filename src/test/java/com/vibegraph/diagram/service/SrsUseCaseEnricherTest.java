package com.vibegraph.diagram.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;
import com.vibegraph.diagram.service.impl.SrsUseCaseEnricher;
import com.vibegraph.diagram.service.impl.SrsUseCaseEnricher.EnrichedModel;

@DisplayName("SrsUseCaseEnricher - SRS-grade tracking decomposition")
class SrsUseCaseEnricherTest {

    private final SrsUseCaseEnricher enricher = new SrsUseCaseEnricher();

    private Actor actor(String id, String name) {
        return Actor.builder().id(id).name(name).source("s").confidence(0.8).build();
    }

    private UseCaseElement uc(String id, String name, String domain) {
        return UseCaseElement.builder().id(id).name(name).domain(domain).level("business")
                .source("s").sourceEndpoint(null).confidence(0.8).build();
    }

    private Relation assoc(String from, String to) {
        return Relation.builder().from(from).to(to).type("association").label(null).confidence(0.8).build();
    }

    @Test
    @DisplayName("decomposes the broad tracking goal into the canonical SRS business use cases")
    void decomposesTrackingGoal() {
        List<Actor> actors = List.of(actor("A_User", "Registered User"));
        List<UseCaseElement> useCases = List.of(uc("UC_ManageTrackings", "Manage Tracking Orders", "Tracking"));
        List<Relation> relations = List.of(assoc("A_User", "UC_ManageTrackings"));

        EnrichedModel out = enricher.enrich(actors, useCases, relations);

        assertThat(out.useCases()).extracting(UseCaseElement::getName)
                .contains("Register Tracking Number", "View Tracking Details", "Review Tracking History",
                        "Synchronize Shipment Status", "Receive Shipment Status Update", "Validate Tracking Number")
                .doesNotContain("Manage Tracking Orders");
    }

    @Test
    @DisplayName("adds the external Carrier Tracking System actor wired only to integration use cases")
    void addsCarrierActor() {
        List<Actor> actors = List.of(actor("A_User", "Registered User"));
        List<UseCaseElement> useCases = List.of(uc("UC_ManageTrackings", "Manage Tracking Orders", "Tracking"));
        List<Relation> relations = List.of(assoc("A_User", "UC_ManageTrackings"));

        EnrichedModel out = enricher.enrich(actors, useCases, relations);

        assertThat(out.actors()).extracting(Actor::getName).contains("Carrier Tracking System");
        // Carrier connects only to integration use cases, never to UI-only user goals.
        assertThat(out.relations())
                .filteredOn(r -> "A_CarrierTrackingSystem".equals(r.getFrom()) && "association".equals(r.getType()))
                .extracting(Relation::getTo)
                .containsExactlyInAnyOrder("UC_SynchronizeShipmentStatus", "UC_ReceiveShipmentStatusUpdate");
    }

    @Test
    @DisplayName("adds only real business include dependencies (register->validate, receive->sync)")
    void addsIncludeExtend() {
        List<Actor> actors = List.of(actor("A_User", "Registered User"));
        List<UseCaseElement> useCases = List.of(uc("UC_ManageTrackings", "Manage Tracking Orders", "Tracking"));
        List<Relation> relations = List.of(assoc("A_User", "UC_ManageTrackings"));

        EnrichedModel out = enricher.enrich(actors, useCases, relations);

        // Registering a tracking number always validates it; receiving a carrier update always syncs
        // the stored shipment status. Viewing details is NOT an include (background auto-sync only).
        assertThat(out.relations())
                .filteredOn(r -> "include".equals(r.getType()))
                .extracting(Relation::getFrom, Relation::getTo)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("UC_RegisterTrackingNumber", "UC_ValidateTrackingNumber"),
                        org.assertj.core.groups.Tuple.tuple("UC_ReceiveShipmentStatusUpdate", "UC_SynchronizeShipmentStatus"));
        // No extend dependencies: the former Receive..>Sync extend was corrected to an include.
        assertThat(out.relations()).noneMatch(r -> "extend".equals(r.getType()));
    }

    @Test
    @DisplayName("user keeps the user-facing tracking goals; the broad goal's edges are removed")
    void rewiresUserAssociations() {
        List<Actor> actors = List.of(actor("A_User", "Registered User"));
        List<UseCaseElement> useCases = List.of(uc("UC_ManageTrackings", "Manage Tracking Orders", "Tracking"));
        List<Relation> relations = List.of(assoc("A_User", "UC_ManageTrackings"));

        EnrichedModel out = enricher.enrich(actors, useCases, relations);

        assertThat(out.relations())
                .filteredOn(r -> "A_User".equals(r.getFrom()) && "association".equals(r.getType()))
                .extracting(Relation::getTo)
                .containsExactlyInAnyOrder("UC_RegisterTrackingNumber", "UC_ViewTrackingDetails",
                        "UC_ReviewTrackingHistory");
        assertThat(out.relations()).noneMatch(r -> "UC_ManageTrackings".equals(r.getTo())
                || "UC_ManageTrackings".equals(r.getFrom()));
    }

    @Test
    @DisplayName("is a no-op for projects without a tracking domain (not overfit)")
    void noOpWithoutTracking() {
        List<Actor> actors = List.of(actor("A_User", "Registered User"));
        List<UseCaseElement> useCases = List.of(uc("UC_ManageProducts", "Manage Products", "Product"));
        List<Relation> relations = List.of(assoc("A_User", "UC_ManageProducts"));

        EnrichedModel out = enricher.enrich(actors, useCases, relations);

        assertThat(out.actors()).extracting(Actor::getName).doesNotContain("Carrier Tracking System");
        assertThat(out.useCases()).extracting(UseCaseElement::getName).containsExactly("Manage Products");
        assertThat(out.relations()).hasSize(1);
    }
}
