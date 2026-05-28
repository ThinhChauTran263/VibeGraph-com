package com.vibegraph.parser.node;

import java.util.List;
import java.util.Map;

/**
 * POJO representing a parsed code element (class, method, field, etc.).
 *
 * This is the parser's output format — NOT a Neo4j @Node entity.
 * The graph module converts NodeData → Neo4j nodes via GraphRepository.
 *
 * See VibeGraph-specs-2month/file-checklist.md
 */
public record NodeData(
    String type,
    String name,
    String fullName,
    String filePath,
    int lineNumber,
    int endLine,
    Map<String, Object> properties
) {
    public static NodeData of(String type, String name, String fullName, String filePath, int lineNumber) {
        return new NodeData(type, name, fullName, filePath, lineNumber, lineNumber, Map.of());
    }

    public static NodeData of(String type, String name, String fullName, String filePath, int lineNumber, int endLine, Map<String, Object> properties) {
        return new NodeData(type, name, fullName, filePath, lineNumber, endLine, properties);
    }
}
