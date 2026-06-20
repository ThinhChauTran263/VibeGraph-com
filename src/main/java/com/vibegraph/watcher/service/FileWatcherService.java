package com.vibegraph.watcher.service;

import java.util.function.Consumer;

/**
 * File system watcher service.
 *
 * <p>Detects {@code .java} file create/modify/delete events under a project root and
 * dispatches debounced {@link FileChangeEvent}s to registered
 * {@link #onFileChange(Consumer) handlers} exactly once per debounce window. The watcher
 * performs no graph mutation itself — turning a change into graph edits (pruning a deleted
 * file's slice, re-parsing created/modified files) is the handler's responsibility (see
 * {@code FileChangeBroadcaster}).
 *
 * <p>Implementations must be safe for concurrent use and must not leak threads or
 * native watch handles after {@link #stopWatching(String)}.
 */
public interface FileWatcherService {

    /**
     * Begin watching {@code rootPath} recursively for {@code .java} changes under {@code projectId}.
     * Idempotent: calling again for an already-watched project safely replaces the existing watcher.
     *
     * @param projectId tenant identifier
     * @param rootPath  absolute path to the project root directory
     * @throws IllegalArgumentException if arguments are blank or {@code rootPath} is not a directory
     */
    void startWatching(String projectId, String rootPath);

    /**
     * Stop watching {@code projectId} and release its watch service, polling thread and
     * any pending debounced work. No-op if the project is not currently watched.
     */
    void stopWatching(String projectId);

    /** @return {@code true} if {@code projectId} is currently being watched. */
    boolean isWatching(String projectId);

    /**
     * Register a handler invoked (after debounce) for every detected change.
     * Handlers receive all event types; exceptions thrown by a handler are logged and isolated.
     */
    void onFileChange(Consumer<FileChangeEvent> handler);
}
