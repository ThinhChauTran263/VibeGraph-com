package com.vibegraph.graph.repository.impl.neo4j;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Whitelist for Neo4j labels, relationship types, and property keys.
 *
 * Neo4j cannot parameterize labels or relationship types — they must be string-
 * interpolated into Cypher. To keep that safe and stable, every interpolated
 * token is validated against a fixed allow-list here before it reaches a query.
 * Anything outside the schema is rejected loudly instead of silently producing
 * malformed Cypher or an injection vector.
 *
 * Source of truth mirrors VibeGraph-specs/neo4j-schema.md and the FE EdgeType /
 * NodeType unions in vibegraph-web/src/types/graph.ts.
 */
public final class GraphSchema {

    /** Stub label applied to on-demand target nodes (library/JDK types, unresolved refs). */
    public static final String EXTERNAL_LABEL = "External";

    private static final Set<String> NODE_LABELS = Set.of(
            "Project", "Package", "File", "Class", "Interface", "Enum",
            "Method", "Field", "Annotation", "Route", EXTERNAL_LABEL
    );

    private static final Set<String> RELATIONSHIP_TYPES = Set.of(
            "OWNS", "CONTAINS", "DEFINES",
            "HAS_METHOD", "HAS_FIELD", "HAS_INNER",
            "EXTENDS", "IMPLEMENTS", "OVERRIDES",
            "IMPORTS", "TYPE_OF", "RETURNS", "PARAMETER_TYPE", "THROWS",
            "CALLS", "INJECTS", "HANDLES_ROUTE", "ANNOTATED_BY"
    );

    /** Safe Cypher identifier for property keys: letter/underscore then word chars. */
    private static final Pattern PROPERTY_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private GraphSchema() {
    }

    /**
     * Validate and return a node label safe to interpolate into Cypher.
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
     * Validate and return a relationship type safe to interpolate into Cypher.
     *
     * @throws IllegalArgumentException if the type is not in the schema.
     */
    public static String relationshipType(String type) {
        if (type == null || !RELATIONSHIP_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown relationship type (not in graph schema): " + type);
        }
        return type;
    }

    /**
     * Validate a property key before it is interpolated as {@code n.<key>}.
     * Keys are parser-controlled constants; this guards against malformed or
     * injected identifiers rather than enumerating every key.
     *
     * @throws IllegalArgumentException if the key is not a safe identifier.
     */
    public static String propertyKey(String key) {
        if (key == null || !PROPERTY_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Illegal property key for Cypher: " + key);
        }
        return key;
    }
}
