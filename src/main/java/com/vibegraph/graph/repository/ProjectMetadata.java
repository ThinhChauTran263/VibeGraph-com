package com.vibegraph.graph.repository;

import java.time.Instant;

/**
 * Durable project metadata read back from the persisted {@code Project} node.
 *
 * <p>Used to recover a project's source root after a backend restart, when the in-memory
 * {@code ProjectService} registry has been cleared but the Neo4j graph (and the
 * {@code Project} node carrying {@code path}) still exists.
 *
 * @param id   stable project identifier (Project node {@code id}/{@code fullName})
 * @param name human-readable display name
 * @param path absolute, server-side source root recorded at analyze time
 * @param createdAt first persisted graph timestamp, when available
 * @param lastAnalyzedAt last graph analysis timestamp, when available
 * @param totalFiles distinct source files represented in the graph
 * @param totalNodes graph nodes excluding the Project metadata node
 * @param totalEdges persisted graph relationships
 */
public record ProjectMetadata(
        String id,
        String name,
        String path,
        Instant createdAt,
        Instant lastAnalyzedAt,
        int totalFiles,
        int totalNodes,
        int totalEdges) {

    public ProjectMetadata(String id, String name, String path) {
        this(id, name, path, null, null, 0, 0, 0);
    }
}
