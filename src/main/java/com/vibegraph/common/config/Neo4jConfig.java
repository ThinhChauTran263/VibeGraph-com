package com.vibegraph.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4j configuration.
 * Spring Data Neo4j auto-configures from application.yaml properties.
 *
 * TODO:
 * - Enable Neo4j repositories scanning
 * - Configure transaction manager if needed
 * - Add custom converters for complex types
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "com.vibegraph")
public class Neo4jConfig {
}
