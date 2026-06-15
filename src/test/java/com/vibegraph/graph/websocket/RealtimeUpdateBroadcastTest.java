package com.vibegraph.graph.websocket;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Captor;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.watcher.service.EventType;
import com.vibegraph.watcher.service.FileChangeEvent;
import com.vibegraph.watcher.service.FileWatcherService;

/**
 * T38 (partial unblock) — verifies the realtime producer→broadcast bridge wiring:
 * a watcher DELETE event re-reads the pruned graph and broadcasts a FULL_UPDATE, while
 * CREATE/MODIFY (no graph mutation yet) do not broadcast. Drives the handler the bridge
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

    @Captor
    ArgumentCaptor<Consumer<FileChangeEvent>> handlerCaptor;

    private FileChangeBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new FileChangeBroadcaster(fileWatcherService, graphRepository, graphUpdateController);
        broadcaster.register();
        verify(fileWatcherService).onFileChange(handlerCaptor.capture());
    }

    private Consumer<FileChangeEvent> handler() {
        return handlerCaptor.getValue();
    }

    @Test
    @DisplayName("DELETE re-reads the graph and broadcasts a FULL_UPDATE for the project")
    void deleteBroadcastsFullUpdate() {
        GraphDataResponse graph = GraphDataResponse.builder()
                .nodes(List.of(NodeDto.builder().id("n1").type("Class").name("A").build()))
                .edges(List.of())
                .build();
        when(graphRepository.getFullGraph("p1")).thenReturn(graph);

        handler().accept(new FileChangeEvent("p1", "com/example/Foo.java", EventType.DELETE, Instant.now()));

        verify(graphRepository).getFullGraph("p1");
        verify(graphUpdateController).broadcastFullUpdate("p1", graph);
    }

    @Test
    @DisplayName("CREATE does not broadcast (incremental re-parse pending)")
    void createDoesNotBroadcast() {
        handler().accept(new FileChangeEvent("p1", "New.java", EventType.CREATE, Instant.now()));

        verifyNoInteractions(graphUpdateController);
        verify(graphRepository, never()).getFullGraph(any());
    }

    @Test
    @DisplayName("MODIFY does not broadcast (incremental re-parse pending)")
    void modifyDoesNotBroadcast() {
        handler().accept(new FileChangeEvent("p1", "Existing.java", EventType.MODIFY, Instant.now()));

        verifyNoInteractions(graphUpdateController);
        verify(graphRepository, never()).getFullGraph(any());
    }

    @Test
    @DisplayName("watchProject delegates to startWatching")
    void watchProjectStartsWatching() {
        broadcaster.watchProject("p1", "/tmp/p1");

        verify(fileWatcherService).startWatching("p1", "/tmp/p1");
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
