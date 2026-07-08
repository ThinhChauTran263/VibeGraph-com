package com.vibegraph.graph.service.impl;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.vibegraph.graph.config.ProjectsProperties;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.repository.ProjectMetadata;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Re-establishes file watchers for previously-analyzed projects on backend startup.
 *
 * <p>Watching is otherwise only armed during an import; after a restart the in-memory watcher
 * state is gone even though the project's source still exists on disk. This runner reads the
 * persisted {@code Project} nodes and re-watches each one whose recorded source root still exists
 * and resolves inside an allowed base (archive workspace root, or the configured allowed-root),
 * so edits keep streaming realtime updates without a manual re-import.
 *
 * <p>Runs late ({@link Ordered#LOWEST_PRECEDENCE}) so schema migration / context init finish first.
 * Watcher failures are swallowed by {@link FileChangeBroadcaster#watchProject} and never abort boot.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class ProjectRewatchRunner implements ApplicationRunner {

    private final GraphRepository graphRepository;
    private final FileChangeBroadcaster fileChangeBroadcaster;
    private final ProjectsProperties projectsProperties;
    private final ArchiveImportProperties archiveImportProperties;

    @Override
    public void run(ApplicationArguments args) {
        int rewatched = 0;
        try {
            for (ProjectMetadata metadata : graphRepository.findAllProjects()) {
                if (metadata == null || metadata.id() == null || metadata.path() == null || metadata.path().isBlank()) {
                    continue;
                }
                if (!isAllowed(metadata.path()) || !isExistingDirectory(metadata.path())) {
                    continue;
                }
                fileChangeBroadcaster.watchProject(metadata.id(), metadata.path());
                rewatched++;
            }
        } catch (RuntimeException e) {
            log.warn("Could not re-watch persisted projects on startup: {}", e.getMessage());
            return;
        }
        if (rewatched > 0) {
            log.info("Re-watching {} persisted project(s) for realtime updates", rewatched);
        }
    }

    private boolean isExistingDirectory(String rawPath) {
        try {
            return Files.isDirectory(Path.of(rawPath));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private boolean isAllowed(String rawPath) {
        Path candidate;
        try {
            candidate = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return false;
        }
        if (startsWithBase(candidate, archiveImportProperties.getWorkspaceRoot())) {
            return true;
        }
        String allowedRoot = projectsProperties.getAllowedRoot();
        if (allowedRoot != null && !allowedRoot.isBlank()) {
            try {
                return startsWithBase(candidate, Path.of(allowedRoot));
            } catch (InvalidPathException ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean startsWithBase(Path candidate, Path base) {
        if (base == null) {
            return false;
        }
        try {
            return candidate.startsWith(base.toAbsolutePath().normalize());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
