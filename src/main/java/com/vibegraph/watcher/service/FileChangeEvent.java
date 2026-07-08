package com.vibegraph.watcher.service;

import java.time.Instant;

/**
 * A single detected change to a watched {@code .java} source file.
 *
 * <p>{@code relativePath} is the path of the changed file relative to the project
 * root, normalized to forward slashes (e.g. {@code src/main/java/com/example/Foo.java}).
 * Handlers resolve it against the project root to recover the absolute path the graph
 * stores (see {@code FileChangeBroadcaster}).
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
