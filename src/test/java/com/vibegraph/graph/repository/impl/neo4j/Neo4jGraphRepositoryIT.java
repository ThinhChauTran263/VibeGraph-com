package com.vibegraph.graph.repository.impl.neo4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Integration test for {@link Neo4jGraphRepository} against a REAL Neo4j instance.
 *
 * <p>This is the verification that {@code @DataNeo4jTest}-style unit mocks cannot give:
 * it proves the raw-Driver Cypher in the repository actually round-trips through Neo4j,
 * that the schema constraints from {@code V1__init_schema.cypher} apply, and that the
 * Route {@code routePath} property (recently fixed from {@code path}) matches the
 * {@code route_unique} constraint key.
 *
 * <p><b>Skips automatically</b> when no Neo4j is reachable at {@code NEO4J_TEST_URI}
 * (default {@code bolt://localhost:7687}), so it never breaks a CI run without a DB.
 * To run locally: {@code docker compose up -d neo4j} then
 * {@code mvnw test -Dtest=Neo4jGraphRepositoryIT}, or {@code mvnw verify} for
 * the full CI-style build.
 *
 * <p>All data is namespaced under a random {@code projectId} and deleted in
 * {@link #cleanup()}, so it never collides with real project data in a shared instance.
 */
@DisplayName("Neo4jGraphRepository (integration, real Neo4j)")
class Neo4jGraphRepositoryIT {

    private static final String URI =
            System.getenv().getOrDefault("NEO4J_TEST_URI", "bolt://localhost:7687");
    private static final String USER =
            System.getenv().getOrDefault("NEO4J_TEST_USERNAME", "neo4j");
    private static final String PASSWORD =
            System.getenv().getOrDefault("NEO4J_TEST_PASSWORD", "vibegraph");

    private static Driver driver;

    private Neo4jGraphRepository repository;
    private String projectId;

    @BeforeAll
    static void connect() {
        try {
            Driver candidate = GraphDatabase.driver(URI, AuthTokens.basic(USER, PASSWORD));
            candidate.verifyConnectivity();
            driver = candidate;
        } catch (Exception ex) {
            // No DB available — every test in this class will be skipped by the assumption below.
            driver = null;
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(driver != null,
                "Neo4j not reachable at " + URI + " — skipping integration test. "
                        + "Run `docker compose up -d neo4j` to enable.");
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
