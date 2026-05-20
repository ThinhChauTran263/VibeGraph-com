package com.vibegraph.graph.repository;

import com.vibegraph.graph.node.FileNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileNodeRepository extends Neo4jRepository<FileNode, Long> {
    Optional<FileNode> findByFilePath(String filePath);
}
