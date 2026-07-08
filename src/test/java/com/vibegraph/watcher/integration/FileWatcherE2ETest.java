package com.vibegraph.watcher.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;
import com.vibegraph.graph.websocket.GraphUpdateEvent;
import com.vibegraph.parser.service.ParserService;
import com.vibegraph.watcher.config.WatcherProperties;
import com.vibegraph.watcher.service.DebouncedEventHandler;
import com.vibegraph.watcher.service.FileWatcherService;
import com.vibegraph.watcher.service.impl.FileWatcherServiceImpl;

@SpringJUnitConfig(classes = FileWatcherE2ETest.TestConfig.class)
@DisplayName("T70 FileWatcher realtime DELETE path (Spring context, no Docker)")
class FileWatcherE2ETest {

    private static final String PROJECT_ID = "t70-light";
    private static final Duration UPDATE_TIMEOUT = Duration.ofSeconds(3);

    @TempDir
    Path projectRoot;

    private final GraphRepository graphRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FileWatcherService fileWatcherService;
    private final DebouncedEventHandler debouncer;

    @Autowired
    FileWatcherE2ETest(
            GraphRepository graphRepository,
            SimpMessagingTemplate messagingTemplate,
            FileWatcherService fileWatcherService,
            DebouncedEventHandler debouncer
    ) {
        this.graphRepository = graphRepository;
        this.messagingTemplate = messagingTemplate;
        this.fileWatcherService = fileWatcherService;
        this.debouncer = debouncer;
    }

    @AfterEach
    void tearDown() {
        fileWatcherService.stopWatching(PROJECT_ID);
        debouncer.shutdown();
    }

    @Test
    @DisplayName("DELETE .java triggers a FULL_UPDATE broadcast within 3 seconds")
    void deleteJavaFileBroadcastsFullUpdateWithinThreeSeconds() throws Exception {
        Path sourceDir = Files.createDirectories(projectRoot.resolve("src"));
        Path sourceFile = sourceDir.resolve("Foo.java");
        Files.writeString(sourceFile, "class Foo {}\n");
        GraphDataResponse prunedGraph = GraphDataResponse.builder()
                .nodes(List.of(NodeDto.builder()
                        .id("com.example.Bar")
                        .type("Class")
                        .name("Bar")
                        .fullName("com.example.Bar")
                        .filePath("src/Bar.java")
                        .build()))
                .edges(List.of())
                .build();
        when(graphRepository.getFullGraph(PROJECT_ID)).thenReturn(prunedGraph);

        fileWatcherService.startWatching(PROJECT_ID, projectRoot.toString());
        Files.delete(sourceFile);

        awaitFullUpdate();
        verify(graphRepository).getFullGraph(PROJECT_ID);
    }

    private void awaitFullUpdate() throws InterruptedException {
        long deadline = System.nanoTime() + UPDATE_TIMEOUT.toNanos();
        AssertionError lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                ArgumentCaptor<GraphUpdateEvent> eventCaptor = ArgumentCaptor.forClass(GraphUpdateEvent.class);
                verify(messagingTemplate).convertAndSend(eq("/topic/projects/" + PROJECT_ID + "/updates"), eventCaptor.capture());
                GraphUpdateEvent event = eventCaptor.getValue();
                assertThat(event.type()).isEqualTo(GraphUpdateEvent.FULL_UPDATE);
                assertThat(event.projectId()).isEqualTo(PROJECT_ID);
                assertThat(event.graph().getNodes()).extracting(NodeDto::getFullName).containsExactly("com.example.Bar");
                return;
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(25);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        WatcherProperties watcherProperties() {
            WatcherProperties properties = new WatcherProperties();
            properties.setDebounceMs(100);
            return properties;
        }

        @Bean
        GraphRepository graphRepository() {
            return mock(GraphRepository.class);
        }

        @Bean
        SimpMessagingTemplate messagingTemplate() {
            return mock(SimpMessagingTemplate.class);
        }

        @Bean
        DebouncedEventHandler debouncedEventHandler(WatcherProperties properties) {
            return new DebouncedEventHandler(properties);
        }

        @Bean
        FileWatcherService fileWatcherService(
                WatcherProperties properties,
                DebouncedEventHandler debouncer
        ) {
            return new FileWatcherServiceImpl(properties, debouncer);
        }

        @Bean
        GraphUpdateController graphUpdateController(SimpMessagingTemplate messagingTemplate) {
            return new GraphUpdateController(messagingTemplate,
                    new com.vibegraph.graph.service.impl.GraphPayloadGuard(),
                    new com.vibegraph.graph.config.GraphPayloadProperties());
        }

        @Bean
        ParserService parserService() {
            return mock(ParserService.class);
        }

        @Bean
        FileChangeBroadcaster fileChangeBroadcaster(
                FileWatcherService fileWatcherService,
                GraphRepository graphRepository,
                GraphUpdateController graphUpdateController,
                ParserService parserService
        ) {
            return new FileChangeBroadcaster(fileWatcherService, graphRepository, graphUpdateController, parserService);
        }
    }
}
