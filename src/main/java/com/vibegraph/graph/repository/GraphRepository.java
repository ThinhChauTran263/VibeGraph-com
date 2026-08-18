package com.vibegraph.graph.repository;

import java.util.List;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Storage abstraction for graph operations.
 * Only implementation in 2-month scope: Neo4jGraphRepository.
 *
 * ArchUnit enforces: no class outside repository/impl/neo4j/ may import org.neo4j.* or
 * org.springframework.data.neo4j.* (except common/config/Neo4jMigrationRunner.java).
 */
public interface GraphRepository {

    void upsertProject(String projectId, String name, String path);

    /**
     * Read back the persisted {@code Project} node metadata, or {@code null} if no
     * project with this id exists in the graph. Lets callers recover a project's
     * source root after the in-memory registry is lost (e.g. backend restart).
     */
    ProjectMetadata findProject(String projectId);

    /**
     * Return metadata for every persisted {@code Project} node. Used to repopulate the project
     * list after the in-memory registry is lost (e.g. backend restart). Returns an empty list
     * when none are persisted.
     */
    java.util.List<ProjectMetadata> findAllProjects();

    void upsertNodes(String projectId, List<NodeData> nodes);

    /**
     * Persist the full graph of one analysis — project node, symbol nodes, and edges — as a
     * SINGLE atomic write (B-M11). Any failure rolls the whole analysis graph back, so a
     * half-written project graph can never be observed.
     *
     * @param progressListener receives one callback per persisted node/edge group, INSIDE the
     *                         write transaction; never {@code null} (use
     *                         {@link UpsertProgressListener#NOOP})
     * @return the number of edges actually persisted (missing endpoints skipped), the truthful
     *         count to report to callers
     */
    int upsertAnalysis(String projectId, String name, String path,
            List<NodeData> nodes, List<EdgeData> edges, UpsertProgressListener progressListener);

    /** Backward-compatible path without per-group write progress. */
    default int upsertAnalysis(String projectId, String name, String path,
            List<NodeData> nodes, List<EdgeData> edges) {
        return upsertAnalysis(projectId, name, path, nodes, edges, UpsertProgressListener.NOOP);
    }

    /**
     * Persist edges between nodes that already exist in the parsed graph. Missing
     * endpoints are skipped rather than materialized as {@code External} stubs.
     *
     * @return the number of edges actually persisted (MERGE'd), which the caller
     *         should report instead of the raw input size.
     */
    int upsertEdges(String projectId, List<EdgeData> edges);

    /** Delete every persisted graph node and relationship for a project id. */
    void deleteProject(String projectId);

    void deleteFile(String projectId, String filePath);

    /**
     * The graph slice owned by one source file: every node stored under {@code filePath} plus
     * every edge touching one of those nodes in either direction (so inbound edges from other
     * files are included and their removal stays observable). Backs the per-file realtime diff
     * (B-M5) without loading the entire project graph.
     */
    GraphDataResponse getFileSlice(String projectId, String filePath);

    GraphDataResponse getFullGraph(String projectId);

    NodeDetailResponse getNodeDetail(String projectId, String nodeId, int hops);

    List<NodeDto> searchNodes(String projectId, String query);

    default ImpactAnalysisResponse getImpact(String projectId, String targetFullName, int maxDepth) {
        return getImpact(projectId, targetFullName, maxDepth, ImpactProfile.DEPENDENCY);
    }

    ImpactAnalysisResponse getImpact(String projectId, String targetFullName, int maxDepth, ImpactProfile profile);
}
