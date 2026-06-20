package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;

/**
 * SRS-quality display-layer enrichment, applied AFTER {@link UseCaseInferenceEngine} and
 * {@link BaLabelBeautifier} and BEFORE {@link UmlUseCaseRenderer}.
 *
 * <p>The inference engine collapses each domain into a single goal use case ("Manage Trackings" →
 * beautified "Manage Tracking Orders"). That is correct for a CRUD-derived view, but a Business
 * Analyst authoring an SRS would decompose shipment tracking into explicit business goals and model
 * the external carrier system as an actor with {@code <<include>>}/{@code <<extend>>} dependencies.
 *
 * <p>This component performs that decomposition <strong>without changing backend route logic,
 * authorization, or the inference engine</strong>: it rewrites the rendered model (actors, use
 * cases, relations) just before rendering. It is intentionally generic and guard-railed — when no
 * tracking domain is present (any other project), {@link #enrich} returns the input unchanged, so it
 * is not overfit to one repository. Extend {@link #TRACKING_DOMAIN_HINTS} or add sibling decomposers
 * to support new domains.
 */
@Component
public class SrsUseCaseEnricher {

    private static final String REL_ASSOCIATION = "association";
    private static final String REL_INCLUDE = "include";
    private static final String LEVEL_BUSINESS = "business";

    /** External carrier integration actor — lives OUTSIDE the system boundary. */
    private static final String CARRIER_ACTOR_ID = "A_CarrierTrackingSystem";
    private static final String CARRIER_ACTOR_NAME = "Carrier Tracking System";

    /** Canonical tracking use case ids (stable, referenced by relations). */
    private static final String UC_REGISTER = "UC_RegisterTrackingNumber";
    private static final String UC_VIEW = "UC_ViewTrackingDetails";
    private static final String UC_HISTORY = "UC_ReviewTrackingHistory";
    private static final String UC_SYNC = "UC_SynchronizeShipmentStatus";
    private static final String UC_RECEIVE = "UC_ReceiveShipmentStatusUpdate";
    private static final String UC_VALIDATE = "UC_ValidateTrackingNumber";

    /** Domain markers that identify the shipment-tracking domain (matched case-insensitively). */
    private static final Set<String> TRACKING_DOMAIN_HINTS = Set.of("tracking", "trackings", "shipment", "parcel");

    /**
     * Enriched, render-ready model. Same shape the renderer consumes.
     */
    public record EnrichedModel(List<Actor> actors, List<UseCaseElement> useCases, List<Relation> relations) {
    }

    /**
     * Decompose the tracking domain into SRS-grade business use cases if present; otherwise return
     * the model untouched.
     */
    public EnrichedModel enrich(List<Actor> actors, List<UseCaseElement> useCases, List<Relation> relations) {
        List<Actor> safeActors = actors == null ? List.of() : actors;
        List<UseCaseElement> safeUseCases = useCases == null ? List.of() : useCases;
        List<Relation> safeRelations = relations == null ? List.of() : relations;

        UseCaseElement trackingUc = findTrackingUseCase(safeUseCases);
        if (trackingUc == null) {
            return new EnrichedModel(safeActors, safeUseCases, safeRelations);
        }

        // The human actor(s) previously associated with the broad tracking goal become the actors for
        // the user-facing decomposed goals. Prefer the canonical Registered User id.
        String humanActorId = resolveHumanActorId(trackingUc.getId(), safeActors, safeRelations);

        // ---- actors: append the external carrier system (outside the boundary) -------------------
        List<Actor> newActors = new ArrayList<>(safeActors);
        if (newActors.stream().noneMatch(a -> CARRIER_ACTOR_ID.equals(a.getId()))) {
            newActors.add(Actor.builder()
                    .id(CARRIER_ACTOR_ID)
                    .name(CARRIER_ACTOR_NAME)
                    .source("external:carrier-integration")
                    .confidence(1.0)
                    .build());
        }

        // ---- use cases: drop the broad goal, add the canonical decomposition ---------------------
        List<UseCaseElement> newUseCases = new ArrayList<>();
        for (UseCaseElement uc : safeUseCases) {
            if (uc != null && !trackingUc.getId().equals(uc.getId())) {
                newUseCases.add(uc);
            }
        }
        newUseCases.add(trackingUseCase(UC_REGISTER, "Register Tracking Number"));
        newUseCases.add(trackingUseCase(UC_VIEW, "View Tracking Details"));
        newUseCases.add(trackingUseCase(UC_HISTORY, "Review Tracking History"));
        newUseCases.add(trackingUseCase(UC_VALIDATE, "Validate Tracking Number"));
        newUseCases.add(trackingUseCase(UC_SYNC, "Synchronize Shipment Status"));
        newUseCases.add(trackingUseCase(UC_RECEIVE, "Receive Shipment Status Update"));

        // ---- relations: drop edges touching the old goal, add the SRS edges ----------------------
        List<Relation> newRelations = new ArrayList<>();
        for (Relation rel : safeRelations) {
            if (rel == null) {
                continue;
            }
            if (trackingUc.getId().equals(rel.getFrom()) || trackingUc.getId().equals(rel.getTo())) {
                continue;
            }
            newRelations.add(rel);
        }

        // Registered User performs the user-facing tracking goals.
        if (humanActorId != null) {
            newRelations.add(association(humanActorId, UC_REGISTER));
            newRelations.add(association(humanActorId, UC_VIEW));
            newRelations.add(association(humanActorId, UC_HISTORY));
        }
        // The carrier system feeds shipment status into the platform.
        newRelations.add(association(CARRIER_ACTOR_ID, UC_SYNC));
        newRelations.add(association(CARRIER_ACTOR_ID, UC_RECEIVE));

        // Real business dependencies only (no implementation-level steps).
        // Registering a tracking number ALWAYS validates it -> include.
        newRelations.add(dependency(UC_REGISTER, UC_VALIDATE, REL_INCLUDE));
        // Receiving a status update from the carrier ALWAYS synchronizes the stored status -> include.
        // (Viewing details does NOT necessarily sync — background auto-sync is not an include, so the
        //  former View -> Sync include is intentionally dropped.)
        newRelations.add(dependency(UC_RECEIVE, UC_SYNC, REL_INCLUDE));

        return new EnrichedModel(newActors, newUseCases, newRelations);
    }

    private UseCaseElement findTrackingUseCase(List<UseCaseElement> useCases) {
        for (UseCaseElement uc : useCases) {
            if (uc == null) {
                continue;
            }
            String domain = uc.getDomain() == null ? "" : uc.getDomain().toLowerCase(Locale.ROOT).trim();
            if (TRACKING_DOMAIN_HINTS.contains(domain)) {
                return uc;
            }
            String name = uc.getName() == null ? "" : uc.getName().toLowerCase(Locale.ROOT);
            if (name.contains("tracking") || name.contains("shipment")) {
                return uc;
            }
        }
        return null;
    }

    /**
     * Find the human actor that drove the broad tracking goal. Prefer the canonical Registered User
     * ({@code A_User}); otherwise fall back to the first associated non-carrier actor so the
     * decomposed user goals stay attached to a real actor.
     */
    private String resolveHumanActorId(String trackingUcId, List<Actor> actors, List<Relation> relations) {
        Set<String> associated = new LinkedHashSet<>();
        for (Relation rel : relations) {
            if (rel != null && REL_ASSOCIATION.equals(rel.getType()) && trackingUcId.equals(rel.getTo())) {
                associated.add(rel.getFrom());
            }
        }
        if (associated.contains("A_User")) {
            return "A_User";
        }
        for (Actor actor : actors) {
            if (actor != null && "A_User".equals(actor.getId())) {
                return "A_User";
            }
        }
        for (String id : associated) {
            if (!CARRIER_ACTOR_ID.equals(id)) {
                return id;
            }
        }
        return null;
    }

    private UseCaseElement trackingUseCase(String id, String name) {
        return UseCaseElement.builder()
                .id(id)
                .name(name)
                .domain("Tracking")
                .level(LEVEL_BUSINESS)
                .source("srs:tracking-decomposition")
                .sourceEndpoint(null)
                .confidence(0.85)
                .build();
    }

    private Relation association(String from, String to) {
        return Relation.builder().from(from).to(to).type(REL_ASSOCIATION).label(null).confidence(0.85).build();
    }

    private Relation dependency(String from, String to, String type) {
        String label = REL_INCLUDE.equals(type) ? "<<include>>" : "<<extend>>";
        return Relation.builder().from(from).to(to).type(type).label(label).confidence(0.8).build();
    }
}
