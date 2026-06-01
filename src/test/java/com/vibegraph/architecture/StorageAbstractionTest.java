package com.vibegraph.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * ArchUnit tests for storage abstraction.
 *
 * Enforces that only classes under graph/repository/impl/neo4j/ (and
 * common/config) depend on Neo4j APIs. The active persistence path is
 * Neo4jGraphRepository (raw Driver + Cypher); the unused Spring Data Neo4j
 * scaffolding (@Node entities, BaseNode, *NodeRepository) was removed.
 *
 * See VibeGraph-specs-2month/architecture.md (Storage Abstraction).
 */
@DisplayName("Storage Abstraction (ArchUnit)")
class StorageAbstractionTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.vibegraph");
    }

    @Test
    @DisplayName("classes outside repository/impl/neo4j must not import org.neo4j.*")
    void classesOutsideNeo4jImplMustNotImportNeo4j() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideOutsideOfPackages(
                        "com.vibegraph.graph.repository.impl.neo4j..",
                        "com.vibegraph.common.config..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.neo4j..", "org.springframework.data.neo4j..");

        rule.check(classes);
    }

    @Test
    @DisplayName("@Node entities must not be referenced from controller layer")
    void nodeEntitiesMustNotLeakToControllers() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.vibegraph..controller..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.vibegraph.graph.node..");

        rule.check(classes);
    }

    @Test
    @DisplayName("services must depend on GraphRepository interface, not Neo4j impl")
    void servicesMustDependOnInterface() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.vibegraph..service..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.vibegraph.graph.repository.impl..");

        rule.check(classes);
    }
}

