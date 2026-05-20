package com.vibegraph.graph.repository;

import com.vibegraph.graph.node.ClassNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClassNodeRepository extends Neo4jRepository<ClassNode, Long> {
    Optional<ClassNode> findByFullName(String fullName);
}
