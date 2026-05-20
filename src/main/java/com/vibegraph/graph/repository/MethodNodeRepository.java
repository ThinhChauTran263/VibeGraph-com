package com.vibegraph.graph.repository;

import com.vibegraph.graph.node.MethodNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MethodNodeRepository extends Neo4jRepository<MethodNode, Long> {
    Optional<MethodNode> findByFullName(String fullName);
}
