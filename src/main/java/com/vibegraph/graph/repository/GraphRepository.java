package com.vibegraph.graph.repository;

import org.springframework.stereotype.Repository;

/**
 * Custom Cypher queries for graph operations.
 *
 * TODO:
 * - getFullGraph(projectId) → all nodes + edges
 * - getNeighbors(nodeId, hops) → N-hop neighborhood
 * - searchNodes(query) → fulltext search
 * - getStatsByType(projectId) → count by node type
 */
@Repository
public class GraphRepository {
    // TODO: Implement custom Cypher queries
}
