/**
 * Neo4j implementation of graph repository interfaces.
 * Contains all Neo4j-specific code (raw Neo4j Java Driver + Cypher).
 *
 * ArchUnit rule: Only classes in this package may import org.neo4j.* or
 * org.springframework.data.neo4j.* (except common/config/Neo4jMigrationRunner.java).
 */
package com.vibegraph.graph.repository.impl.neo4j;
