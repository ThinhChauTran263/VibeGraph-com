package com.vibegraph.watcher.service;

import java.time.Instant;

/**
 * A single detected change to a watched {@code .java} source file.
 *
 * <p>{@code relativePath} is the path of the changed file relative to the project
 * root, normalized to forward slashes (e.g. {@code src/main/java/com/example/Foo.java}).
 * It is the exact value passed to {@link com.vibegraph.graph.repository.GraphRepository#deleteFile(String, String)}
 * on a {@link EventType#DELETE}.
 *
 * @param projectId    tenant identifier the change belongs to
 * @param relativePath project-root-relative path of the changed file (forward-slash separated)
 * @param type         kind of change
 * @param timestamp    when the change was observed
 */
public record FileChangeEvent(
        String projectId,
        String relativePath,
        EventType type,
        Instant timestamp
) {
}
