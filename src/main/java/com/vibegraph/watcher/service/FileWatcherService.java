package com.vibegraph.watcher.service;

/**
 * File system watcher service.
 * Detects .java file changes and triggers incremental re-analysis.
 *
 * TODO: Define methods
 */
public interface FileWatcherService {

    void startWatching(String projectId, String rootPath);

    void stopWatching(String projectId);
}
