package com.vibegraph.graph.repository;

import java.util.List;

/**
 * Storage abstraction for graph operations.
 * Only implementation in 2-month scope: Neo4jGraphRepository.
 *
 * ArchUnit enforces: no class outside repository/impl/neo4j/ may import org.neo4j.* or
 * org.springframework.data.neo4j.* (except common/config/Neo4jConfig.java).
 */
public interface GraphRepository {

    void upsertProject(String projectId, String name, String path);

    void upsertNodes(String projectId, List<?> nodes);

    void upsertEdges(String projectId, List<?> edges);

    void deleteFile(String projectId, String filePath);

    Object getFullGraph(String projectId);

    Object getNeighborhood(String projectId, String nodeId, int hops);

    List<?> searchNodes(String projectId, String query);

    List<?> getImpact(String projectId, String targetFullName, int maxDepth);
}
