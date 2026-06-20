package com.vibegraph.graph.websocket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.parser.node.ParseResult;
import com.vibegraph.parser.service.ParserService;
import com.vibegraph.watcher.service.EventType;
import com.vibegraph.watcher.service.FileChangeEvent;
import com.vibegraph.watcher.service.FileWatcherService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridges {@link FileWatcherService} file-change events to realtime graph-update broadcasts.
 *
 * <p>On any watched {@code .java} change this re-parses ONLY the changed file, replaces that
 * file's slice of the graph (prune old → upsert new), diffs the project graph before/after,
 * and pushes an {@code INCREMENTAL} delta (added/removed) to
 * {@code /topic/projects/{projectId}/updates}. The rest of the project is never re-analyzed,
 * so adding a class C to files A/B simply adds C; deleting a file removes only its nodes.
 *
 * <p>The stored node {@code filePath} is the absolute source path captured at analyze time, so
 * the watcher's project-relative path is resolved against the project root (recorded in
 * {@link #watchProject}) before pruning/diffing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileChangeBroadcaster {

    private final FileWatcherService fileWatcherService;
    private final GraphRepository graphRepository;
    private final GraphUpdateController graphUpdateController;
    private final ParserService parserService;

    /**
     * projectId → normalized absolute source root, captured when watching starts. Needed to turn
     * a watcher's project-relative path back into the absolute {@code filePath} the graph stores.
     */
    private final Map<String, Path> projectRoots = new ConcurrentHashMap<>();

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
            projectRoots.put(projectId, Path.of(rootPath).toAbsolutePath().normalize());
        } catch (RuntimeException e) {
            log.warn("Could not start file watcher for project={} root={}: {}",
                    projectId, rootPath, e.getMessage());
        }
    }

    /** Stop watching a project (e.g. on project deletion). Safe for unknown projects. */
    public void unwatch(String projectId) {
        fileWatcherService.stopWatching(projectId);
        projectRoots.remove(projectId);
    }

    private void onFileChange(FileChangeEvent event) {
        String projectId = event.projectId();
        try {
            Path root = projectRoots.get(projectId);
            if (root == null) {
                // Root unknown (watch not started through watchProject): we cannot resolve the
                // stored absolute path, so converge the client with a full snapshot instead.
                graphUpdateController.broadcastFullUpdate(projectId, graphRepository.getFullGraph(projectId));
                return;
            }

            Path absolutePath = root.resolve(event.relativePath()).normalize();
            String storedPath = absolutePath.toString();

            GraphDataResponse before = graphRepository.getFullGraph(projectId);

            // Replace just this file's slice: prune its previous nodes/edges, then (for add/edit)
            // re-parse the single file and upsert. No other file is touched or re-analyzed.
            graphRepository.deleteFile(projectId, storedPath);
            if (event.type() != EventType.DELETE && Files.exists(absolutePath)) {
                ParseResult parsed = parserService.parseFile(absolutePath);
                graphRepository.upsertNodes(projectId, parsed.getNodes());
                graphRepository.upsertEdges(projectId, parsed.getEdges());
            }

            GraphDataResponse after = graphRepository.getFullGraph(projectId);

            GraphChangeSet added = computeUpserts(before, after, storedPath);
            GraphRemoval removed = computeRemovals(before, after);
            graphUpdateController.broadcastIncremental(projectId, added, null, removed);
            log.debug("Broadcast incremental update: project={} file={} type={}",
                    projectId, event.relativePath(), event.type());
        } catch (RuntimeException e) {
            log.error("Failed to broadcast graph update for project={} file={}: {}",
                    projectId, event.relativePath(), e.getMessage(), e);
        }
    }

    /**
     * Nodes/edges the client should upsert: every node that is new (absent before) or belongs to
     * the changed file (re-sent so edits to existing symbols refresh), plus edges that are new or
     * touch one of those nodes. Returns {@code null} when there is nothing to add.
     */
    private GraphChangeSet computeUpserts(GraphDataResponse before, GraphDataResponse after, String storedPath) {
        Set<String> beforeNodeIds = nodeIds(before);
        List<NodeDto> nodes = new ArrayList<>();
        Set<String> upsertNodeIds = new HashSet<>();
        for (NodeDto node : safeNodes(after)) {
            if (!beforeNodeIds.contains(node.getId()) || storedPath.equals(node.getFilePath())) {
                nodes.add(node);
                upsertNodeIds.add(node.getId());
            }
        }

        Set<String> beforeEdgeIds = edgeIds(before);
        List<EdgeDto> edges = new ArrayList<>();
        for (EdgeDto edge : safeEdges(after)) {
            boolean isNew = !beforeEdgeIds.contains(edge.getId());
            boolean touchesFile = upsertNodeIds.contains(edge.getSource()) || upsertNodeIds.contains(edge.getTarget());
            if (isNew || touchesFile) {
                edges.add(edge);
            }
        }

        if (nodes.isEmpty() && edges.isEmpty()) {
            return null;
        }
        return new GraphChangeSet(nodes, edges);
    }

    /** Ids present before the change but gone afterwards = removed. Returns {@code null} when none. */
    private GraphRemoval computeRemovals(GraphDataResponse before, GraphDataResponse after) {
        Set<String> afterNodeIds = nodeIds(after);
        Set<String> afterEdgeIds = edgeIds(after);

        List<String> removedNodes = new ArrayList<>();
        for (String id : nodeIds(before)) {
            if (!afterNodeIds.contains(id)) {
                removedNodes.add(id);
            }
        }
        List<String> removedEdges = new ArrayList<>();
        for (String id : edgeIds(before)) {
            if (!afterEdgeIds.contains(id)) {
                removedEdges.add(id);
            }
        }

        if (removedNodes.isEmpty() && removedEdges.isEmpty()) {
            return null;
        }
        return new GraphRemoval(removedNodes, removedEdges);
    }

    private static List<NodeDto> safeNodes(GraphDataResponse graph) {
        return graph != null && graph.getNodes() != null ? graph.getNodes() : List.of();
    }

    private static List<EdgeDto> safeEdges(GraphDataResponse graph) {
        return graph != null && graph.getEdges() != null ? graph.getEdges() : List.of();
    }

    private static Set<String> nodeIds(GraphDataResponse graph) {
        Set<String> ids = new HashSet<>();
        for (NodeDto node : safeNodes(graph)) {
            ids.add(node.getId());
        }
        return ids;
    }

    private static Set<String> edgeIds(GraphDataResponse graph) {
        Set<String> ids = new HashSet<>();
        for (EdgeDto edge : safeEdges(graph)) {
            ids.add(edge.getId());
        }
        return ids;
    }
}
