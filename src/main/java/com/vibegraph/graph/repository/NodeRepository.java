package com.vibegraph.graph.repository;

import com.vibegraph.common.node.BaseNode;

import java.util.List;
import java.util.Optional;

/**
 * Generic repository interface for any node type extending BaseNode.
 *
 * This is the storage abstraction — implementations live in
 * {@code repository/impl/neo4j/} and are the only place Neo4j APIs may leak.
 *
 * See VibeGraph-specs-2month/file-checklist.md (graph/repository section).
 *
 * @param <T> a concrete @Node entity type extending BaseNode
 */
public interface NodeRepository<T extends BaseNode> {

    Optional<T> findById(String projectId, Long id);

    Optional<T> findByFullName(String projectId, String fullName);

    List<T> findAll(String projectId);

    T save(T node);

    List<T> saveAll(List<T> nodes);

    void deleteById(String projectId, Long id);

    void deleteByProjectId(String projectId);

    long countByProjectId(String projectId);
}
