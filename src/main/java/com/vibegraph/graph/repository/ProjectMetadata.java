package com.vibegraph.graph.repository;

/**
 * Minimal, durable project metadata read back from the persisted {@code Project} node.
 *
 * <p>Used to recover a project's source root after a backend restart, when the in-memory
 * {@code ProjectService} registry has been cleared but the Neo4j graph (and the
 * {@code Project} node carrying {@code path}) still exists.
 *
 * @param id   stable project identifier (Project node {@code id}/{@code fullName})
 * @param name human-readable display name
 * @param path absolute, server-side source root recorded at analyze time
 */
public record ProjectMetadata(String id, String name, String path) {
}
