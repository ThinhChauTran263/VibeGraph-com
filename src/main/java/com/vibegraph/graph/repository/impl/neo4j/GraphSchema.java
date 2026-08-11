package com.vibegraph.graph.repository.impl.neo4j;

import java.util.regex.Pattern;

import com.vibegraph.graph.model.GraphVocabulary;

/**
 * Whitelist for Neo4j labels, relationship types, and property keys.
 *
 * Neo4j cannot parameterize labels or relationship types - they must be string-
 * interpolated into Cypher. To keep that safe and stable, every interpolated
 * token is validated against a fixed allow-list before it reaches a query.
 * Anything outside the schema is rejected loudly instead of silently producing
 * malformed Cypher or an injection vector.
 *
 * The label / relationship-type allow-list lives in the storage-neutral
 * {@link GraphVocabulary}; this class adds the Cypher-specific property-key
 * validation on top.
 */
public final class GraphSchema {

    /** Legacy placeholder label kept for migration cleanup; parser no longer emits it. */
    public static final String EXTERNAL_LABEL = GraphVocabulary.EXTERNAL_LABEL;

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
        return GraphVocabulary.nodeLabel(label);
    }

    /**
     * Validate and return a relationship type safe to interpolate into Cypher.
     *
     * @throws IllegalArgumentException if the type is not in the schema.
     */
    public static String relationshipType(String type) {
        return GraphVocabulary.relationshipType(type);
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
