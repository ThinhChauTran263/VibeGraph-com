package com.vibegraph.graph.model;

import java.util.Set;

/**
 * Storage-neutral allow-list of graph node labels and relationship types.
 *
 * This is the single source of truth for the vocabulary shared by the Neo4j
 * schema whitelist (GraphSchema) and HTTP-boundary filtering
 * (GraphResponseFilter), so service-layer code can validate types without
 * depending on the repository implementation.
 *
 * Source of truth mirrors VibeGraph-specs-2month/neo4j-schema.md and the FE EdgeType /
 * NodeType unions in vibegraph-web/src/types/graph.ts.
 */
public final class GraphVocabulary {

    /** Legacy placeholder label kept for migration cleanup; parser no longer emits it. */
    public static final String EXTERNAL_LABEL = "External";

    private static final Set<String> NODE_LABELS = Set.of(
            "Project", "Package", "File", "Class", "Interface", "Enum",
            "Record", "DBModel", "Method", "Constructor", "Field", "Annotation",
            "LocalVariable", "Route", "APIEndpoint", EXTERNAL_LABEL
    );

    private static final Set<String> RELATIONSHIP_TYPES = Set.of(
            "OWNS", "CONTAINS", "DEFINES",
            "HAS_METHOD", "HAS_FIELD", "HAS_INNER",
            "HAS_RELATION",
            "EXTENDS", "IMPLEMENTS", "OVERRIDES",
            "IMPORTS", "TYPE_OF", "RETURNS", "PARAMETER_TYPE", "THROWS",
            "CALLS", "INSTANTIATES", "INJECTS", "HANDLES_ROUTE", "ANNOTATED_BY",
            "READS", "WRITES", "CATCHES", "STEP_IN_FLOW",
            "PUBLISHES_EVENT", "LISTENS_EVENT", "TRIGGERS",
            "RESOLVES_TO", "CALLS_DYNAMIC", "DISPATCH_CANDIDATES"
    );

    private GraphVocabulary() {
    }

    /**
     * Validate and return a node label from the graph schema.
     *
     * @throws IllegalArgumentException if the label is not in the schema.
     */
    public static String nodeLabel(String label) {
        if (label == null || !NODE_LABELS.contains(label)) {
            throw new IllegalArgumentException("Unknown node label (not in graph schema): " + label);
        }
        return label;
    }

    /**
     * Validate and return a relationship type from the graph schema.
     *
     * @throws IllegalArgumentException if the type is not in the schema.
     */
    public static String relationshipType(String type) {
        if (type == null || !RELATIONSHIP_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown relationship type (not in graph schema): " + type);
        }
        return type;
    }
}
