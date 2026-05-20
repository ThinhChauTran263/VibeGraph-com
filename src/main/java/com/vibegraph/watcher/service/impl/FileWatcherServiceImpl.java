package com.vibegraph.watcher.service.impl;

import com.vibegraph.watcher.config.WatcherProperties;
import com.vibegraph.watcher.service.FileWatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Java WatchService-based implementation.
 *
 * TODO:
 * - Use java.nio.file.WatchService
 * - Register paths recursively
 * - Filter by extension (.java)
 * - Debounce with DebouncedEventHandler
 * - Run in virtual thread (Java 21)
 * - Handle ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
 * - Trigger AnalyzeService.analyzeFile() on change
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileWatcherServiceImpl implements FileWatcherService {

    private final WatcherProperties properties;

    @Override
    public void startWatching(String projectId, String rootPath) {
        // TODO: Implement
    }

    @Override
    public void stopWatching(String projectId) {
        // TODO: Implement
    }
}
