package com.vibegraph.parser.node;

import java.util.Map;

/**
 * POJO representing a relationship between two code elements.
 *
 * This is the parser's output format — NOT a Neo4j relationship entity.
 * The graph module converts EdgeData → Neo4j relationships via GraphRepository.
 *
 * See VibeGraph-specs-2month/file-checklist.md
 */
public record EdgeData(
    String type,
    String sourceFullName,
    String targetFullName,
    Map<String, Object> properties
) {
    public static EdgeData of(String type, String sourceFullName, String targetFullName) {
        return new EdgeData(type, sourceFullName, targetFullName, Map.of());
    }

    public static EdgeData of(String type, String sourceFullName, String targetFullName, Map<String, Object> properties) {
        return new EdgeData(type, sourceFullName, targetFullName, properties);
    }
}
