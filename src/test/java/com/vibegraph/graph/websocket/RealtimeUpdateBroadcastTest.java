package com.vibegraph.graph.websocket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Captor;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.parser.node.NodeData;
import com.vibegraph.parser.node.ParseResult;
import com.vibegraph.parser.service.ParserService;
import com.vibegraph.watcher.service.EventType;
import com.vibegraph.watcher.service.FileChangeEvent;
import com.vibegraph.watcher.service.FileWatcherService;

/**
 * Verifies the realtime producer→broadcast bridge: a watched file change re-parses only that
 * file and broadcasts an INCREMENTAL delta (added/removed). Drives the handler the bridge
 * registers via {@code onFileChange}, so it proves the chain without flaky filesystem timing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Realtime update broadcast bridge (FileChangeBroadcaster)")
class RealtimeUpdateBroadcastTest {

    @Mock
    FileWatcherService fileWatcherService;
    @Mock
    GraphRepository graphRepository;
    @Mock
    GraphUpdateController graphUpdateController;
    @Mock
    ParserService parserService;

    @Captor
    ArgumentCaptor<Consumer<FileChangeEvent>> handlerCaptor;
    @Captor
    ArgumentCaptor<GraphChangeSet> addedCaptor;
    @Captor
    ArgumentCaptor<GraphRemoval> removedCaptor;

    @TempDir
    Path projectRoot;

    private FileChangeBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new FileChangeBroadcaster(
                fileWatcherService, graphRepository, graphUpdateController, parserService);
        broadcaster.register();
        verify(fileWatcherService).onFileChange(handlerCaptor.capture());
    }

    private Consumer<FileChangeEvent> handler() {
        return handlerCaptor.getValue();
    }

    private String storedPath(String relative) {
        return projectRoot.toAbsolutePath().normalize().resolve(relative).normalize().toString();
    }

    private static GraphDataResponse graph(List<NodeDto> nodes, List<EdgeDto> edges) {
        return GraphDataResponse.builder().nodes(nodes).edges(edges).build();
    }

    @Test
    @DisplayName("DELETE prunes the file's slice and broadcasts its nodes as removed")
    void deleteBroadcastsIncrementalRemoval() {
        broadcaster.watchProject("p1", projectRoot.toString());
        String path = storedPath("src/Bar.java");
        NodeDto bar = NodeDto.builder().id("com.example.Bar").type("Class").name("Bar")
                .fullName("com.example.Bar").filePath(path).build();
        when(graphRepository.getFileSlice("p1", path))
                .thenReturn(graph(List.of(bar), List.of()))   // before
                .thenReturn(graph(List.of(), List.of()));      // after prune

        handler().accept(new FileChangeEvent("p1", "src/Bar.java", EventType.DELETE, Instant.now()));

        verify(graphRepository).deleteFile("p1", path);
        verify(graphUpdateController).broadcastIncremental(eq("p1"), addedCaptor.capture(), isNull(), removedCaptor.capture());
        assertThat(removedCaptor.getValue().nodeIds()).containsExactly("com.example.Bar");
    }

    @Test
    @DisplayName("CREATE re-parses only the new file and broadcasts its nodes as added")
    void createBroadcastsIncrementalAddition() throws Exception {
        broadcaster.watchProject("p1", projectRoot.toString());
        Files.createDirectories(projectRoot.resolve("src"));
        Path file = projectRoot.resolve("src/New.java");
        Files.writeString(file, "class New {}\n");
        String path = storedPath("src/New.java");

        NodeData parsedNode = NodeData.of("Class", "New", "com.example.New", path, 1);
        when(parserService.parseFile(any(Path.class)))
                .thenReturn(ParseResult.builder().filePath(path).nodes(List.of(parsedNode)).edges(List.of()).build());

        NodeDto added = NodeDto.builder().id("com.example.New").type("Class").name("New")
                .fullName("com.example.New").filePath(path).build();
        when(graphRepository.getFileSlice("p1", path))
                .thenReturn(graph(List.of(), List.of()))        // before (A,B only — empty here)
                .thenReturn(graph(List.of(added), List.of()));  // after upsert (+C)

        handler().accept(new FileChangeEvent("p1", "src/New.java", EventType.CREATE, Instant.now()));

        verify(parserService).parseFile(projectRoot.toAbsolutePath().normalize().resolve("src/New.java").normalize());
        verify(graphRepository).upsertNodes(eq("p1"), any());
        verify(graphUpdateController).broadcastIncremental(eq("p1"), addedCaptor.capture(), isNull(), removedCaptor.capture());
        assertThat(addedCaptor.getValue().nodes()).extracting(NodeDto::getId).containsExactly("com.example.New");
    }

    @Test
    @DisplayName("falls back to a full snapshot when the project root is unknown")
    void fallbackFullUpdateWhenRootUnknown() {
        GraphDataResponse full = graph(
                List.of(NodeDto.builder().id("n1").type("Class").name("A").build()), List.of());
        when(graphRepository.getFullGraph("p1")).thenReturn(full);

        handler().accept(new FileChangeEvent("p1", "src/Foo.java", EventType.DELETE, Instant.now()));

        verify(graphRepository).getFullGraph("p1");
        verify(graphUpdateController).broadcastFullUpdate("p1", full);
    }

    @Test
    @DisplayName("watchProject delegates to startWatching")
    void watchProjectStartsWatching() {
        broadcaster.watchProject("p1", projectRoot.toString());

        verify(fileWatcherService).startWatching("p1", projectRoot.toString());
    }

    @Test
    @DisplayName("watchProject swallows watcher failures so import/analyze never breaks")
    void watchProjectSwallowsFailures() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("rootPath is not a directory"))
                .when(fileWatcherService).startWatching(eq("p1"), any());

        assertThatCode(() -> broadcaster.watchProject("p1", "/gone")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unwatch delegates to stopWatching")
    void unwatchStopsWatching() {
        broadcaster.unwatch("p1");

        verify(fileWatcherService).stopWatching("p1");
    }
}
