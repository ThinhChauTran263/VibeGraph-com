package com.vibegraph.graph.dto.response;

/**
 * Node type contract - the FE/BE source of truth.
 *
 * Mirrors {@code vibegraph-web/src/types/graph.ts} NodeType and
 * the Neo4j labels in VibeGraph-specs-2month/neo4j-schema.md section 2.
 *
 * The {@code NodeDto.type} field MUST be one of these values (use {@link #label()}).
 */
public enum NodeTypeEnum {
    PROJECT("Project"),
    PACKAGE("Package"),
    FILE("File"),
    CLASS("Class"),
    INTERFACE("Interface"),
    ENUM("Enum"),
    RECORD("Record"),
    DB_MODEL("DBModel"),
    METHOD("Method"),
    CONSTRUCTOR("Constructor"),
    FIELD("Field"),
    ANNOTATION("Annotation"),
    ROUTE("Route"),
    API_ENDPOINT("APIEndpoint"),
    EXTERNAL("External");

    private final String label;

    NodeTypeEnum(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
