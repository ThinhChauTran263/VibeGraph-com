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
    HAS_RELATION("HAS_RELATION"),
    EXTENDS("EXTENDS"),
    IMPLEMENTS("IMPLEMENTS"),
    OVERRIDES("OVERRIDES"),
    IMPORTS("IMPORTS"),
    TYPE_OF("TYPE_OF"),
    RETURNS("RETURNS"),
    PARAMETER_TYPE("PARAMETER_TYPE"),
    THROWS("THROWS"),
    CALLS("CALLS"),
    INSTANTIATES("INSTANTIATES"),
    INJECTS("INJECTS"),
    HANDLES_ROUTE("HANDLES_ROUTE"),
    ANNOTATED_BY("ANNOTATED_BY"),
    READS("READS"),
    WRITES("WRITES"),
    CATCHES("CATCHES"),
    STEP_IN_FLOW("STEP_IN_FLOW"),
    PUBLISHES_EVENT("PUBLISHES_EVENT"),
    LISTENS_EVENT("LISTENS_EVENT"),
    TRIGGERS("TRIGGERS"),
    RESOLVES_TO("RESOLVES_TO"),
    CALLS_DYNAMIC("CALLS_DYNAMIC"),
    DISPATCH_CANDIDATES("DISPATCH_CANDIDATES");

    private final String label;

    EdgeTypeEnum(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
