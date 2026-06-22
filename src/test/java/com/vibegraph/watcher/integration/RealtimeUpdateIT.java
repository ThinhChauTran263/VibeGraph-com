package com.vibegraph.watcher.integration;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.VibeGraphApplication;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.websocket.GraphUpdateEvent;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;
import com.vibegraph.watcher.service.DebouncedEventHandler;
import com.vibegraph.watcher.service.FileWatcherService;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = VibeGraphApplication.class,
        properties = {
                "vibegraph.watcher.debounce-ms=100",
                "spring.main.allow-bean-definition-overriding=true"
        })
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("T70 realtime FileWatcher DELETE E2E (STOMP + Testcontainers Neo4j)")
class RealtimeUpdateIT {

    private static final Duration UPDATE_TIMEOUT = Duration.ofSeconds(3);

    @Container
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5-community");

    private static Driver driver;

    @LocalServerPort
    int port;

    @TempDir
    Path projectRoot;

    private final GraphRepository graphRepository;
    private final FileWatcherService fileWatcherService;
    private final DebouncedEventHandler debouncer;

    @Autowired
    RealtimeUpdateIT(
            GraphRepository graphRepository,
            FileWatcherService fileWatcherService,
            DebouncedEventHandler debouncer
    ) {
        this.graphRepository = graphRepository;
        this.fileWatcherService = fileWatcherService;
        this.debouncer = debouncer;
    }

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", NEO4J::getAdminPassword);
    }

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

    private String projectId;

    @AfterEach
    void tearDown() {
        fileWatcherService.stopWatching(projectId);
        debouncer.shutdown();
    }

    @Test
    @DisplayName("DELETE of a .java file prunes Neo4j and broadcasts FULL_UPDATE within 3 seconds")
    void deleteBroadcastsUpdateWithin3s() throws Exception {
        projectId = "t70-" + UUID.randomUUID().toString().substring(0, 8);
        Path sourceDir = Files.createDirectories(projectRoot.resolve("src"));
        Path deletedFile = sourceDir.resolve("Changed.java");
        Files.writeString(deletedFile, "package com.example; class Changed {}\n");
        seedGraph(projectId, projectRoot);

        WebSocketStompClient stompClient = stompClient();
        StompSession session = null;
        try {
            session = connect(stompClient);
            CompletableFuture<GraphUpdateEvent> update = subscribe(session, projectId);
            fileWatcherService.startWatching(projectId, projectRoot.toString());

            Files.delete(deletedFile);

            GraphUpdateEvent event = update.get(UPDATE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(event.type()).isEqualTo(GraphUpdateEvent.FULL_UPDATE);
            assertThat(event.projectId()).isEqualTo(projectId);
            assertThat(event.graph().getNodes())
                    .extracting(NodeDto::getFullName)
                    .doesNotContain("com.example.Changed")
                    .contains("com.example.Other");
            assertThat(graphRepository.getFullGraph(projectId).getNodes())
                    .extracting(NodeDto::getFullName)
                    .doesNotContain("com.example.Changed")
                    .contains("com.example.Other");
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            cleanup(projectId);
        }
    }

    private void seedGraph(String projectId, Path root) {
        // The broadcaster prunes by the ABSOLUTE filePath it resolves at watch time
        // (root.resolve(relative).normalize()), so seed the nodes with that same absolute path —
        // a relative "src/Changed.java" would never match the DELETE's Cypher and the node would
        // (incorrectly) survive the delete.
        Path base = root.toAbsolutePath().normalize();
        String changedPath = base.resolve("src/Changed.java").toString();
        String otherPath = base.resolve("src/Other.java").toString();
        graphRepository.upsertProject(projectId, projectId, root.toString());
        graphRepository.upsertNodes(projectId, List.of(
                NodeData.of("Class", "Changed", "com.example.Changed", changedPath, 1, 1, java.util.Map.of()),
                NodeData.of("Class", "Other", "com.example.Other", otherPath, 1, 1, java.util.Map.of())));
        graphRepository.upsertEdges(projectId, List.of(
                EdgeData.of("CALLS", "com.example.Changed", "lib.Orphan.call()", java.util.Map.of()),
                EdgeData.of("CALLS", "com.example.Other", "lib.Shared.call()", java.util.Map.of())));
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private StompSession connect(WebSocketStompClient stompClient) throws Exception {
        CompletableFuture<StompSession> future = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/graph-updates/websocket",
                new WebSocketHttpHeaders(),
                new StompSessionHandlerAdapter() {});
        return future.get(UPDATE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<GraphUpdateEvent> subscribe(StompSession session, String projectId) throws Exception {
        CompletableFuture<GraphUpdateEvent> update = new CompletableFuture<>();
        session.subscribe("/topic/projects/" + projectId + "/updates", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GraphUpdateEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                update.complete((GraphUpdateEvent) payload);
            }
        });
        Thread.sleep(100);
        return update;
    }

    private void cleanup(String projectId) {
        if (driver == null) {
            return;
        }
        try (Session session = driver.session()) {
            session.run("MATCH (n {projectId: $projectId}) DETACH DELETE n", java.util.Map.of("projectId", projectId));
        }
    }

}
