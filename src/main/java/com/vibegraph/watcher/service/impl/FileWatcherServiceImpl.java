package com.vibegraph.watcher.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.vibegraph.watcher.config.WatcherProperties;
import com.vibegraph.watcher.service.DebouncedEventHandler;
import com.vibegraph.watcher.service.EventType;
import com.vibegraph.watcher.service.FileChangeEvent;
import com.vibegraph.watcher.service.FileWatcherService;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link java.nio.file.WatchService}-based watcher.
 *
 * <p>Each watched project gets its own {@link WatchService}, a virtual-thread poll loop,
 * and a per-project event buffer keyed by relative path (so repeated events for the same
 * file collapse to the latest). Raw events are validated (must live under the project
 * root), filtered (ignored directories + watched extensions), buffered, then flushed once
 * per debounce window.
 *
 * <p>Dispatch policy: every buffered change (create, modify, delete) is emitted to the
 * registered {@link #onFileChange(Consumer) handlers} exactly once per debounce window. This
 * watcher performs no graph mutation itself; turning a change into graph edits — pruning the
 * deleted file's slice and re-parsing created/modified files — is the handler's responsibility
 * (see {@code FileChangeBroadcaster}, which resolves the stored absolute path and broadcasts an
 * incremental delta).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileWatcherServiceImpl implements FileWatcherService {

    private final WatcherProperties properties;
    private final DebouncedEventHandler debouncer;

    private final Map<String, WatchRegistration> registrations = new ConcurrentHashMap<>();
    private final List<Consumer<FileChangeEvent>> changeHandlers = new CopyOnWriteArrayList<>();

    @Override
    public void onFileChange(Consumer<FileChangeEvent> handler) {
        if (handler != null) {
            changeHandlers.add(handler);
        }
    }

    @Override
    public boolean isWatching(String projectId) {
        return projectId != null && registrations.containsKey(projectId);
    }

    @Override
    public void startWatching(String projectId, String rootPath) {
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
        if (!StringUtils.hasText(rootPath)) {
            throw new IllegalArgumentException("rootPath must not be blank");
        }
        if (!properties.isEnabled()) {
            log.info("File watcher disabled; not watching project={}", projectId);
            return;
        }

        Path root = Path.of(rootPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("rootPath is not a directory: " + root);
        }

        // Idempotent: replace any existing watcher for this project.
        if (registrations.containsKey(projectId)) {
            log.debug("Project {} already watched; restarting watcher", projectId);
            stopWatching(projectId);
        }

        try {
            WatchService watchService = root.getFileSystem().newWatchService();
            WatchRegistration registration = new WatchRegistration(projectId, root, watchService);
            registerRecursively(root, registration);
            registrations.put(projectId, registration);

            Thread pollThread = Thread.ofVirtual()
                    .name("file-watcher-" + projectId)
                    .start(() -> pollLoop(registration));
            registration.pollThread = pollThread;

            log.info("Started watching project={} root={}", projectId, root);
        } catch (IOException e) {
            log.error("Failed to start watcher for project={} root={}: {}", projectId, root, e.getMessage(), e);
            throw new UncheckedIOException("Failed to start file watcher for project " + projectId, e);
        }
    }

    @Override
    public void stopWatching(String projectId) {
        if (projectId == null) {
            return;
        }
        WatchRegistration registration = registrations.remove(projectId);
        if (registration == null) {
            return;
        }
        registration.running = false;
        debouncer.cancel(projectId);
        try {
            registration.watchService.close();
        } catch (IOException e) {
            log.warn("Error closing watch service for project={}: {}", projectId, e.getMessage());
        }
        if (registration.pollThread != null) {
            registration.pollThread.interrupt();
        }
        registration.buffer.clear();
        log.info("Stopped watching project={}", projectId);
    }

    @PreDestroy
    public void stopAll() {
        for (String projectId : List.copyOf(registrations.keySet())) {
            stopWatching(projectId);
        }
    }

    // ------------------------------------------------------------------
    // Event pipeline (package-private for deterministic unit testing)
    // ------------------------------------------------------------------

    /**
     * Validate, filter and buffer a raw change, then (re)arm the debounce flush.
     * Events for paths outside the project root, in ignored directories, or with a
     * non-watched extension are dropped here.
     */
    void enqueue(String projectId, Path absolutePath, EventType type) {
        WatchRegistration registration = registrations.get(projectId);
        if (registration == null) {
            return;
        }
        Path normalized = absolutePath.toAbsolutePath().normalize();
        if (!normalized.startsWith(registration.root)) {
            log.warn("Ignoring change outside project root: project={} path={}", projectId, normalized);
            return;
        }
        String relativePath = registration.root.relativize(normalized).toString().replace('\\', '/');
        if (!shouldProcess(relativePath)) {
            log.trace("Filtered change: project={} path={}", projectId, relativePath);
            return;
        }
        registration.buffer.put(relativePath, new FileChangeEvent(projectId, relativePath, type, Instant.now()));
        debouncer.debounce(projectId, () -> flush(projectId));
    }

    /** @return whether {@code relativePath} should be processed (not ignored, watched extension). */
    boolean shouldProcess(String relativePath) {
        for (String segment : relativePath.split("/")) {
            if (properties.getIgnoredPaths().contains(segment)) {
                return false;
            }
        }
        return properties.getWatchedExtensions().stream().anyMatch(relativePath::endsWith);
    }

    /** Drain the per-project buffer and dispatch each buffered change exactly once. */
    void flush(String projectId) {
        WatchRegistration registration = registrations.get(projectId);
        if (registration == null) {
            return;
        }
        List<FileChangeEvent> batch = new ArrayList<>();
        for (String key : List.copyOf(registration.buffer.keySet())) {
            FileChangeEvent event = registration.buffer.remove(key);
            if (event != null) {
                batch.add(event);
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        log.info("Dispatching {} change(s) for project={}", batch.size(), projectId);
        for (FileChangeEvent event : batch) {
            notifyHandlers(event);
        }
    }

    private void notifyHandlers(FileChangeEvent event) {
        for (Consumer<FileChangeEvent> handler : changeHandlers) {
            try {
                handler.accept(event);
            } catch (RuntimeException e) {
                log.error("onFileChange handler failed for project={} file={}: {}",
                        event.projectId(), event.relativePath(), e.getMessage(), e);
            }
        }
    }

    // ------------------------------------------------------------------
    // WatchService plumbing
    // ------------------------------------------------------------------

    private void registerRecursively(Path start, WatchRegistration registration) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (!dir.equals(start) && properties.getIgnoredPaths().contains(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                WatchKey key = dir.register(
                        registration.watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                registration.keyToDir.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void pollLoop(WatchRegistration registration) {
        while (registration.running) {
            WatchKey key;
            try {
                key = registration.watchService.take();
            } catch (ClosedWatchServiceException | InterruptedException e) {
                break;
            }
            Path dir = registration.keyToDir.get(key);
            if (dir != null) {
                for (WatchEvent<?> rawEvent : key.pollEvents()) {
                    if (rawEvent.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Path child = dir.resolve(((Path) rawEvent.context()));
                    EventType type = toEventType(rawEvent.kind());
                    // A newly created directory must be registered so nested files are watched too.
                    if (type == EventType.CREATE && Files.isDirectory(child)) {
                        try {
                            registerRecursively(child, registration);
                        } catch (IOException e) {
                            log.warn("Failed to register new subdirectory {}: {}", child, e.getMessage());
                        }
                    }
                    enqueue(registration.projectId, child, type);
                }
            }
            boolean stillValid = key.reset();
            if (!stillValid) {
                registration.keyToDir.remove(key);
                if (registration.keyToDir.isEmpty()) {
                    break;
                }
            }
        }
    }

    private static EventType toEventType(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return EventType.CREATE;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return EventType.DELETE;
        }
        return EventType.MODIFY;
    }

    /** Per-project watch state. */
    private static final class WatchRegistration {
        private final String projectId;
        private final Path root;
        private final WatchService watchService;
        private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();
        private final Map<String, FileChangeEvent> buffer = new ConcurrentHashMap<>();
        private volatile boolean running = true;
        private volatile Thread pollThread;

        WatchRegistration(String projectId, Path root, WatchService watchService) {
            this.projectId = projectId;
            this.root = root;
            this.watchService = watchService;
        }
    }
}
