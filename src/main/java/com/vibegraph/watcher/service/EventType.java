package com.vibegraph.watcher.service;

/**
 * Kind of file system change detected by the {@link FileWatcherService}.
 *
 * <p>Maps to {@code java.nio.file.StandardWatchEventKinds}:
 * {@code ENTRY_CREATE -> CREATE}, {@code ENTRY_MODIFY -> MODIFY},
 * {@code ENTRY_DELETE -> DELETE}.
 */
public enum EventType {
    CREATE,
    MODIFY,
    DELETE
}
