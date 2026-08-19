package com.vibegraph.graph.repository.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.repository.ProjectMetadata;
import com.vibegraph.graph.repository.impl.neo4j.Neo4jGraphRepository;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

import lombok.extern.slf4j.Slf4j;

/**
 * Write-through caching decorator over {@link Neo4jGraphRepository} for the hottest read:
 * {@link #getFullGraph(String)}. Before this cache, EVERY MCP tool call re-ran the full
 * per-project graph load against Neo4j.
 *
 * <p>Correctness: ArchUnit ({@code StorageAbstractionTest}) guarantees every persistence
 * path in the codebase goes through the {@link GraphRepository} interface, and this bean is
 * {@link Primary} — so every mutation (project upsert/delete, node/edge upsert, file delete)
 * flows through here and invalidates that project's snapshot. There is no bypass path that
 * could leave the cache stale. A TTL bounds staleness defensively anyway (e.g. manual DB edits).
 *
 * <p>Cached {@link GraphDataResponse} instances are shared across callers and MUST be treated
 * as read-only. Existing consumers already do: GraphView copies into its own structures, the
 * analyzers stream into new lists, and FileChangeBroadcaster only iterates.
 *
 * <p>All other reads (node detail, search, impact) are pass-through — they are index-backed
 * point lookups after the V2 {@code :Symbol} migration and not worth caching.
 */
@Repository
@Primary
@Slf4j
public class CachingGraphRepository implements GraphRepository {

    /** Defensive staleness bound; write-through invalidation is the primary mechanism. */
    static final long TTL_MILLIS = 5 * 60 * 1000L;
    /** Memory bound: at most this many project snapshots are retained. */
    static final int MAX_ENTRIES = 32;

    private final Neo4jGraphRepository delegate;
    private final LongSupplier clock;
    private final Map<String, CacheEntry> snapshots = new ConcurrentHashMap<>();

    @Autowired
    public CachingGraphRepository(Neo4jGraphRepository delegate) {
        this(delegate, System::currentTimeMillis);
    }

    /** Test seam: an injectable clock makes eviction order deterministic. */
    CachingGraphRepository(Neo4jGraphRepository delegate, LongSupplier clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    // ---- cached read -------------------------------------------------------------------------

    @Override
    public GraphDataResponse getFullGraph(String projectId) {
        long now = clock.getAsLong();
        CacheEntry entry = snapshots.get(projectId);
        if (entry != null && now - entry.loadedAt < TTL_MILLIS) {
            return entry.graph;
        }
        GraphDataResponse graph = delegate.getFullGraph(projectId);
        snapshots.put(projectId, new CacheEntry(graph, now));
        pruneIfOverflowing();
        return graph;
    }

    private void invalidate(String projectId) {
        snapshots.remove(projectId);
    }

    /**
     * Bound memory by dropping the oldest snapshot when too many projects are cached.
     * B-L2: a put() adds at most one entry, so a single oldest-eviction pass is enough —
     * the previous while-nested-for rescanned the whole map per evicted entry.
     */
    private void pruneIfOverflowing() {
        if (snapshots.size() <= MAX_ENTRIES) {
            return;
        }
        snapshots.entrySet().stream()
                .min(Map.Entry.comparingByValue(Comparator.comparingLong(CacheEntry::loadedAt)))
                .map(Map.Entry::getKey)
                .ifPresent(oldestKey -> {
                    snapshots.remove(oldestKey);
                    log.debug("Evicted oldest graph snapshot from cache: {}", oldestKey);
                });
    }

    // ---- writes: delegate then invalidate ----------------------------------------------------

    @Override
    public void upsertProject(String projectId, String name, String path) {
        delegate.upsertProject(projectId, name, path);
        invalidate(projectId);
    }

    @Override
    public void upsertNodes(String projectId, List<NodeData> nodes) {
        delegate.upsertNodes(projectId, nodes);
        invalidate(projectId);
    }

    @Override
    public int upsertEdges(String projectId, List<EdgeData> edges) {
        int persisted = delegate.upsertEdges(projectId, edges);
        invalidate(projectId);
        return persisted;
    }

    @Override
    public int upsertAnalysis(String projectId, String name, String path,
            List<NodeData> nodes, List<EdgeData> edges) {
        int persisted = delegate.upsertAnalysis(projectId, name, path, nodes, edges);
        invalidate(projectId);
        return persisted;
    }

    @Override
    public int upsertAnalysis(String projectId, String name, String path,
            List<NodeData> nodes, List<EdgeData> edges, WriteProgress progress) {
        int persisted = delegate.upsertAnalysis(projectId, name, path, nodes, edges, progress);
        invalidate(projectId);
        return persisted;
    }

    @Override
    public void deleteProject(String projectId) {
        delegate.deleteProject(projectId);
        invalidate(projectId);
    }

    @Override
    public void deleteFile(String projectId, String filePath) {
        delegate.deleteFile(projectId, filePath);
        invalidate(projectId);
    }

    // ---- pass-through reads ------------------------------------------------------------------

    @Override
    public ProjectMetadata findProject(String projectId) {
        return delegate.findProject(projectId);
    }

    @Override
    public List<ProjectMetadata> findAllProjects() {
        return delegate.findAllProjects();
    }

    @Override
    public GraphDataResponse getFileSlice(String projectId, String filePath) {
        // Point-read for the per-file diff (B-M5): index-backed, not worth snapshot caching.
        return delegate.getFileSlice(projectId, filePath);
    }

    @Override
    public NodeDetailResponse getNodeDetail(String projectId, String nodeId, int hops) {
        return delegate.getNodeDetail(projectId, nodeId, hops);
    }

    @Override
    public List<NodeDto> searchNodes(String projectId, String query) {
        return delegate.searchNodes(projectId, query);
    }

    @Override
    public ImpactAnalysisResponse getImpact(String projectId, String targetFullName, int maxDepth, ImpactProfile profile) {
        return delegate.getImpact(projectId, targetFullName, maxDepth, profile);
    }

    private record CacheEntry(GraphDataResponse graph, long loadedAt) {
    }
}
