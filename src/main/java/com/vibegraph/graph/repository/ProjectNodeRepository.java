package com.vibegraph.graph.repository;

import com.vibegraph.graph.node.ProjectNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectNodeRepository extends Neo4jRepository<ProjectNode, Long> {
    Optional<ProjectNode> findByProjectId(String projectId);
}
