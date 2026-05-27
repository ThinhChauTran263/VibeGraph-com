package com.vibegraph.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests for storage abstraction.
 *
 * Rule (per architecture spec):
 *   No class outside `com.vibegraph.graph.repository.impl.neo4j.*` may import
 *   `org.neo4j.*` or `org.springframework.data.neo4j.*`,
 *   except `com.vibegraph.common.config.Neo4jConfig`.
 *
 * Rationale: keeps Neo4j as a swappable backend behind GraphRepository.
 *
 * Run: mvn test -Dtest=StorageAbstractionTest
 *
 * TODO: enable once ArchUnit is added to pom.xml.
 *   <dependency>
 *     <groupId>com.tngtech.archunit</groupId>
 *     <artifactId>archunit-junit5</artifactId>
 *     <version>1.3.0</version>
 *     <scope>test</scope>
 *   </dependency>
 */
@DisplayName("Storage Abstraction (ArchUnit)")
@Disabled("Chờ ArchUnit dependency được thêm vào pom.xml")
class StorageAbstractionTest {

    @Test
    @DisplayName("classes outside repository/impl/neo4j must not import org.neo4j.*")
    void classesOutsideNeo4jImplMustNotImportNeo4j() {
        // ArchRuleDefinition.noClasses()
        //     .that().resideOutsideOfPackages(
        //         "com.vibegraph.graph.repository.impl.neo4j..",
        //         "com.vibegraph.common.config..")
        //     .should().dependOnClassesThat()
        //     .resideInAnyPackage("org.neo4j..", "org.springframework.data.neo4j..")
        //     .check(classes);
    }

    @Test
    @DisplayName("@Node entities must not be referenced from controller layer")
    void nodeEntitiesMustNotLeakToControllers() {
        // ArchRuleDefinition.noClasses()
        //     .that().resideInAPackage("com.vibegraph.graph.controller..")
        //     .should().dependOnClassesThat()
        //     .resideInAPackage("com.vibegraph.graph.node..")
        //     .check(classes);
    }

    @Test
    @DisplayName("services must depend on GraphRepository interface, not Neo4j impl")
    void servicesMustDependOnInterface() {
        // ArchRuleDefinition.noClasses()
        //     .that().resideInAPackage("com.vibegraph.graph.service..")
        //     .should().dependOnClassesThat()
        //     .resideInAPackage("com.vibegraph.graph.repository.impl..")
        //     .check(classes);
    }
}
