package com.vibegraph.graph.repository.impl.neo4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Integration test for {@link Neo4jGraphRepository} against a REAL Neo4j instance,
 * provided on demand by Testcontainers.
 *
 * <p>This is the verification that {@code @DataNeo4jTest}-style unit mocks cannot give:
 * it proves the raw-Driver Cypher in the repository actually round-trips through Neo4j,
 * and that the Route {@code routePath} property (fixed from {@code path}) is persisted.
 *
 * <p>Runs by default: a {@code neo4j:5-community} container is started automatically, so
 * the test no longer depends on a developer running Neo4j locally. The class is
 * {@code disabledWithoutDocker = true}, so on a machine with no Docker daemon it is
 * skipped (rather than erroring); CI must provide Docker so this coverage is included.
 *
 * <p>All data is namespaced under a random {@code projectId} and deleted in
 * {@link #cleanup()}.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Neo4jGraphRepository (integration, real Neo4j via Testcontainers)")
class Neo4jGraphRepositoryIT {

    @Container
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5-community");

    private static Driver driver;

    private Neo4jGraphRepository repository;
    private String projectId;

    @BeforeAll
    static void connect() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(),
                AuthTokens.basic("neo4j", NEO4J.getAdminPassword()));
        driver.verifyConnectivity();
    }

    @AfterAll
    static void close() {
        if (driver != null) {
            driver.close();
        }
    }

    @BeforeEach
    void setUp() {
        repository = new Neo4jGraphRepository(driver);
        projectId = "it-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void cleanup() {
        if (driver == null) return;
        try (Session session = driver.session()) {
            session.run("MATCH (n {projectId: $projectId}) DETACH DELETE n",
                    Map.of("projectId", projectId));
        }
    }

    @Test
    @DisplayName("upsertNodes + getFullGraph round-trips a Class node through real Neo4j")
    void shouldRoundTripNodes() {
        NodeData clazz = NodeData.of(
                "Class", "UserService", "com.example.UserService",
                "src/UserService.java", 1, 50,
                Map.of("springLayer", "SERVICE", "visibility", "public"));

        repository.upsertProject(projectId, projectId, "/tmp/demo");
        repository.upsertNodes(projectId, List.of(clazz));

        GraphDataResponse graph = repository.getFullGraph(projectId);

        NodeDto persisted = graph.getNodes().stream()
                .filter(n -> "com.example.UserService".equals(n.getFullName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Class node not persisted"));
        assertThat(persisted.getType()).isEqualTo("Class");
        assertThat(persisted.getName()).isEqualTo("UserService");
        assertThat(persisted.getProperties()).containsEntry("springLayer", "SERVICE");
    }

    @Test
    @DisplayName("upsertEdges returns a truthful persisted count and creates an External stub target")
    void shouldPersistEdgesWithExternalStub() {
        NodeData caller = NodeData.of(
                "Method", "save", "com.example.UserService.save(User)",
                "src/UserService.java", 10, 12, Map.of("paramTypes", List.of("User")));

        repository.upsertProject(projectId, projectId, "/tmp/demo");
        repository.upsertNodes(projectId, List.of(caller));

        // Target points at an unparsed library type — repository must MERGE an External stub.
        EdgeData call = EdgeData.of("CALLS",
                "com.example.UserService.save(User)",
                "org.example.Repository.persist(User)",
                Map.of("lineNumber", 11));

        int persisted = repository.upsertEdges(projectId, List.of(call));

        assertThat(persisted).isEqualTo(1);

        GraphDataResponse graph = repository.getFullGraph(projectId);
        EdgeDto edge = graph.getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CALLS edge not persisted"));
        assertThat(edge.getSource()).isEqualTo("com.example.UserService.save(User)");
        assertThat(edge.getTarget()).isEqualTo("org.example.Repository.persist(User)");
    }

    @Test
    @DisplayName("upsertNodes enriches a pre-existing External stub in place — no duplicate, edge re-points to the real node")
    void shouldEnrichExternalStubWithoutDuplicate() {
        String targetFullName = "com.example.UserRepository.persist(User)";
        repository.upsertProject(projectId, projectId, "/tmp/demo");

        // 1. An edge whose target isn't parsed yet → upsertEdges MERGEs an External stub.
        repository.upsertEdges(projectId, List.of(EdgeData.of("CALLS",
                "com.example.UserService.save(User)", targetFullName, Map.of("lineNumber", 11))));

        // 2. Later the real Method node arrives with the SAME fullName as the stub.
        repository.upsertNodes(projectId, List.of(NodeData.of(
                "Method", "persist", targetFullName,
                "src/UserRepository.java", 20, 22, Map.of("paramTypes", List.of("User")))));

        try (Session session = driver.session()) {
            // No duplicate: exactly one node carries that fullName.
            long count = session.run(
                    "MATCH (n {projectId: $projectId, fullName: $fullName}) RETURN count(n) AS c",
                    Map.of("projectId", projectId, "fullName", targetFullName))
                    .single().get("c").asLong();
            assertThat(count).isEqualTo(1L);

            // The node is now the real label and no longer an External stub.
            List<String> labels = session.run(
                    "MATCH (n {projectId: $projectId, fullName: $fullName}) RETURN labels(n) AS labels",
                    Map.of("projectId", projectId, "fullName", targetFullName))
                    .single().get("labels").asList(org.neo4j.driver.Value::asString);
            assertThat(labels).contains("Method").doesNotContain("External");

            // The old CALLS edge is attached to the real (now Method) node, not stuck on a stub.
            long callsIntoMethod = session.run(
                    "MATCH (:Method {projectId: $projectId, fullName: $fullName})<-[r:CALLS]-() RETURN count(r) AS c",
                    Map.of("projectId", projectId, "fullName", targetFullName))
                    .single().get("c").asLong();
            assertThat(callsIntoMethod).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("Route node persists with routePath property matching the route_unique constraint key")
    void shouldPersistRouteWithRoutePathProperty() {
        // Regression guard for the path -> routePath fix: the route_unique constraint is
        // (projectId, httpMethod, routePath). A Route written with the old "path" key left
        // routePath null and silently bypassed the constraint.
        NodeData route = NodeData.of(
                "Route", "GET /api/users/{id}", "GET /api/users/{id}",
                "", 5, 7,
                Map.of("httpMethod", "GET", "routePath", "/api/users/{id}"));

        repository.upsertProject(projectId, projectId, "/tmp/demo");
        repository.upsertNodes(projectId, List.of(route));

        GraphDataResponse graph = repository.getFullGraph(projectId);
        NodeDto persisted = graph.getNodes().stream()
                .filter(n -> "Route".equals(n.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Route node not persisted"));
        assertThat(persisted.getProperties()).containsEntry("routePath", "/api/users/{id}");
        assertThat(persisted.getProperties()).containsEntry("httpMethod", "GET");
    }

    @Test
    @DisplayName("data is isolated by projectId — a second project does not see the first project's nodes")
    void shouldIsolateByProjectId() {
        repository.upsertProject(projectId, projectId, "/tmp/demo");
        repository.upsertNodes(projectId, List.of(NodeData.of(
                "Class", "Alpha", "com.example.Alpha", "src/Alpha.java", 1, 5, Map.of())));

        String otherProject = "it-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            GraphDataResponse otherGraph = repository.getFullGraph(otherProject);
            assertThat(otherGraph.getNodes()).isEmpty();
        } finally {
            try (Session session = driver.session()) {
                session.run("MATCH (n {projectId: $projectId}) DETACH DELETE n",
                        Map.of("projectId", otherProject));
            }
        }
    }
}
