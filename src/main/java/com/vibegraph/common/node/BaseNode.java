package com.vibegraph.common.node;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;

/**
 * Base abstract class for all Neo4j @Node entities.
 * Provides common fields like id, createdAt, updatedAt.
 *
 * TODO:
 * - Add audit fields
 * - Add version field for optimistic locking
 */
public abstract class BaseNode {

    @Id
    @GeneratedValue
    private Long id;

    public Long getId() {
        return id;
    }
}
