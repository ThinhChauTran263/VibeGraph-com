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

    void upsertNodes(String projectId, List<NodeData> nodes);

    /**
     * Persist edges. Any edge whose target node does not exist as a parsed node
     * gets a minimal {@code External} stub node created on demand so the edge is
     * never silently dropped.
     *
     * @return the number of edges actually persisted (MERGE'd), which the caller
     *         should report instead of the raw input size.
     */
    int upsertEdges(String projectId, List<EdgeData> edges);

    void deleteFile(String projectId, String filePath);

    GraphDataResponse getFullGraph(String projectId);

    GraphDataResponse getNeighborhood(String projectId, String nodeId, int hops);

    NodeDetailResponse getNodeDetail(String projectId, String nodeId, int hops);

    List<NodeDto> searchNodes(String projectId, String query);

    default ImpactAnalysisResponse getImpact(String projectId, String targetFullName, int maxDepth) {
        return getImpact(projectId, targetFullName, maxDepth, ImpactProfile.DEPENDENCY);
    }

    ImpactAnalysisResponse getImpact(String projectId, String targetFullName, int maxDepth, ImpactProfile profile);
}
