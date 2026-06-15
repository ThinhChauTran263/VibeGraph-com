package com.vibegraph.graph.websocket;

import org.springframework.stereotype.Component;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.watcher.service.EventType;
import com.vibegraph.watcher.service.FileChangeEvent;
import com.vibegraph.watcher.service.FileWatcherService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridges {@link FileWatcherService} file-change events to realtime graph-update broadcasts
 * (T38). This is the missing producer→broadcast link between the watcher (T37) and the
 * STOMP publisher (T36).
 *
 * <p>On a {@code .java} DELETE the watcher has already pruned the file from the graph store
 * ({@code GraphRepository.deleteFile}, T25) by the time handlers run, so this re-reads the
 * resulting full graph and pushes a {@code FULL_UPDATE} to
 * {@code /topic/projects/{projectId}/updates}. CREATE/MODIFY are intentionally not broadcast:
 * incremental re-parse is not wired ({@code ParserService.parseFileWithCache} is deferred to
 * Sprint 2), so there is no graph change to publish for additive edits yet.
 *
 * <p>Also owns the watcher start/stop lifecycle helpers used by the import/analyze flows.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileChangeBroadcaster {

    private final FileWatcherService fileWatcherService;
    private final GraphRepository graphRepository;
    private final GraphUpdateController graphUpdateController;

    /** Register the change→broadcast handler once, at startup. */
    @PostConstruct
    void register() {
        fileWatcherService.onFileChange(this::onFileChange);
        log.info("Registered realtime graph-update broadcaster on file changes");
    }

    /**
     * Start watching an analyzed project's source root for realtime updates. Failures
     * (missing/cleaned path, watcher disabled, IO error) are logged and swallowed so they
     * never break the import/analyze flow that triggered the watch.
     */
    public void watchProject(String projectId, String rootPath) {
        try {
            fileWatcherService.startWatching(projectId, rootPath);
        } catch (RuntimeException e) {
            log.warn("Could not start file watcher for project={} root={}: {}",
                    projectId, rootPath, e.getMessage());
        }
    }

    /** Stop watching a project (e.g. on project deletion). Safe for unknown projects. */
    public void unwatch(String projectId) {
        fileWatcherService.stopWatching(projectId);
    }

    private void onFileChange(FileChangeEvent event) {
        try {
            if (event.type() == EventType.DELETE) {
                GraphDataResponse graph = graphRepository.getFullGraph(event.projectId());
                graphUpdateController.broadcastFullUpdate(event.projectId(), graph);
                log.debug("Broadcast graph update after DELETE: project={} file={}",
                        event.projectId(), event.relativePath());
            } else {
                // CREATE/MODIFY: no graph mutation yet (incremental re-parse deferred to Sprint 2).
                log.debug("File {} for project={} not broadcast (incremental re-parse pending): {}",
                        event.type(), event.projectId(), event.relativePath());
            }
        } catch (RuntimeException e) {
            log.error("Failed to broadcast graph update for project={} file={}: {}",
                    event.projectId(), event.relativePath(), e.getMessage(), e);
        }
    }
}
