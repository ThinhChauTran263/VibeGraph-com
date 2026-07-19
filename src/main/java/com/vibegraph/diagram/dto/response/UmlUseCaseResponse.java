package com.vibegraph.diagram.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rich intermediate model for a business-level UML Use Case diagram.
 *
 * <p>This model holds inferred business actors and verb-phrased use cases, plus both a
 * Mermaid fallback rendering and a standard PlantUML source for proper UML export/rendering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlUseCaseResponse {

    /** Always {@code "usecase"} for this diagram family. */
    private String diagramType;

    /** Rendering style: {@code "uml"} for the business diagram. */
    private String style;

    /** Layout mode: {@code "flat"} or {@code "grouped"}. */
    private String mode;

    /** Human-readable system boundary name (project name). */
    private String systemName;

    private List<Actor> actors;
    private List<UseCaseElement> useCases;
    private List<Relation> relations;

    /** Inference notes surfaced to the user (e.g. role guessed from HTTP method). */
    private List<String> warnings;

    /** Mermaid fallback source (compatible with mermaid ^11). */
    private String mermaidSyntax;

    /** Standard PlantUML source ({@code @startuml ... @enduml}); null for non-UML styles. */
    private String plantUmlSyntax;

    /**
     * Per-view projections of the SAME canonical model (R4): one view per actor (everything that
     * actor can reach) and one per business domain. Pure filtering/projection — never a re-inference —
     * so a view can never disagree with the full diagram. Null/empty for non-UML styles.
     */
    private List<UseCaseView> views;

    /**
     * A projected sub-diagram: a faithful induced sub-graph of the canonical model, scoped to one
     * actor or one domain, with its own Mermaid/PlantUML rendering.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UseCaseView {
        /** {@code "actor"} or {@code "domain"}. */
        private String viewType;
        /** The actor name or domain this view is scoped to. */
        private String title;
        private List<Actor> actors;
        private List<UseCaseElement> useCases;
        private List<Relation> relations;
        private String mermaidSyntax;
        private String plantUmlSyntax;
    }

    /**
     * Business actor (e.g. {@code Admin}, {@code User}).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Actor {
        /** Stable deterministic id (e.g. {@code A_Admin}). */
        private String id;
        private String name;
        /** How this actor was inferred (e.g. {@code "path:/admin"}, {@code "http-method-fallback"}). */
        private String source;
        /** Inference confidence in {@code [0,1]}. */
        private Double confidence;
    }

    /**
     * Business use case (verb phrase), concrete detail or grouped summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UseCaseElement {
        /** Stable deterministic id (e.g. {@code UC_CreateProduct}). */
        private String id;
        /** Verb phrase (e.g. {@code "Create product"}, {@code "Manage products"}). */
        private String name;
        /** Business domain (e.g. {@code "Product"}). */
        private String domain;
        /** {@code "summary"} (grouped) or {@code "detail"} (single CRUD action). */
        private String level;
        /** How this use case was inferred. */
        private String source;
        /** Originating endpoint id (e.g. {@code "POST /api/products"}); null for summary use cases. */
        private String sourceEndpoint;
        private Double confidence;
    }

    /**
     * Edge between elements: actor-to-usecase association or summary-to-detail include.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Relation {
        /** Source element id. */
        private String from;
        /** Target element id. */
        private String to;
        /** {@code "association"} or {@code "include"}. */
        private String type;
        /** Optional stereotype label (e.g. {@code "<<include>>"}). */
        private String label;
        private Double confidence;
    }
}
