package com.vibegraph.graph.dto.response;

/**
 * Edge type contract — the FE/BE source of truth.
 *
 * Mirrors {@code vibegraph-web/src/types/graph.ts} EdgeType and
 * the relationship types in VibeGraph-specs-2month/neo4j-schema.md §3.
 *
 * The {@code EdgeDto.type} field MUST be one of these values (use {@link #label()}).
 */
public enum EdgeTypeEnum {
    OWNS("OWNS"),
    CONTAINS("CONTAINS"),
    DEFINES("DEFINES"),
    HAS_METHOD("HAS_METHOD"),
    HAS_FIELD("HAS_FIELD"),
    HAS_INNER("HAS_INNER"),
    EXTENDS("EXTENDS"),
    IMPLEMENTS("IMPLEMENTS"),
    OVERRIDES("OVERRIDES"),
    IMPORTS("IMPORTS"),
    TYPE_OF("TYPE_OF"),
    RETURNS("RETURNS"),
    PARAMETER_TYPE("PARAMETER_TYPE"),
    THROWS("THROWS"),
    CALLS("CALLS"),
    INJECTS("INJECTS"),
    HANDLES_ROUTE("HANDLES_ROUTE"),
    ANNOTATED_BY("ANNOTATED_BY");

    private final String label;

    EdgeTypeEnum(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
