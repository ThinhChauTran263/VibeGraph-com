package com.vibegraph.graph.websocket;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.dto.response.ProjectStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("GraphUpdateController")
class GraphUpdateControllerTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    GraphUpdateController controller;

    @Test
    @DisplayName("broadcastStatus publishes the payload to the project status topic")
    void broadcastStatusPublishesToTopic() {
        controller.broadcastStatus("p1", "ANALYZING", 0);

        ArgumentCaptor<ProjectStatusEvent> captor = ArgumentCaptor.forClass(ProjectStatusEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/p1/status"), captor.capture());
        ProjectStatusEvent event = captor.getValue();
        assertThat(event.projectId()).isEqualTo("p1");
        assertThat(event.status()).isEqualTo("ANALYZING");
        assertThat(event.progress()).isZero();
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("ProjectStatus overload sends the enum name as status")
    void broadcastStatusEnumOverload() {
        controller.broadcastStatus("p1", ProjectStatus.ANALYZED, 100);

        ArgumentCaptor<ProjectStatusEvent> captor = ArgumentCaptor.forClass(ProjectStatusEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/p1/status"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("ANALYZED");
        assertThat(captor.getValue().progress()).isEqualTo(100);
    }

    @Test
    @DisplayName("progress is clamped to 0..100")
    void broadcastStatusClampsProgress() {
        controller.broadcastStatus("p1", "ANALYZING", 150);
        controller.broadcastStatus("p2", "ANALYZING", -5);

        ArgumentCaptor<ProjectStatusEvent> captor = ArgumentCaptor.forClass(ProjectStatusEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/p1/status"), captor.capture());
        assertThat(captor.getValue().progress()).isEqualTo(100);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/p2/status"), captor.capture());
        assertThat(captor.getValue().progress()).isZero();
    }

    @Test
    @DisplayName("broadcastFullUpdate publishes a FULL_UPDATE to the project updates topic")
    void broadcastFullUpdatePublishesToUpdatesTopic() {
        GraphDataResponse graph = GraphDataResponse.builder()
                .nodes(List.of(NodeDto.builder().id("n1").type("Class").name("A").build()))
                .edges(List.of())
                .build();

        controller.broadcastFullUpdate("p1", graph);

        ArgumentCaptor<GraphUpdateEvent> captor = ArgumentCaptor.forClass(GraphUpdateEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/p1/updates"), captor.capture());
        GraphUpdateEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo("FULL_UPDATE");
        assertThat(event.projectId()).isEqualTo("p1");
        assertThat(event.graph()).isSameAs(graph);
        assertThat(event.added()).isNull();
        assertThat(event.modified()).isNull();
        assertThat(event.removed()).isNull();
    }

    @Test
    @DisplayName("broadcastIncremental publishes added/modified/removed diffs to the updates topic")
    void broadcastIncrementalPublishesDiff() {
        GraphChangeSet added = new GraphChangeSet(
                List.of(NodeDto.builder().id("n2").type("Class").name("B").build()),
                List.of(EdgeDto.builder().id("e1").source("n1").target("n2").type("CALLS").build()));
        GraphChangeSet modified = new GraphChangeSet(
                List.of(NodeDto.builder().id("n1").type("Interface").name("A").build()), null);
        GraphRemoval removed = new GraphRemoval(List.of("n9"), List.of("e9"));

        controller.broadcastIncremental("p1", added, modified, removed);

        ArgumentCaptor<GraphUpdateEvent> captor = ArgumentCaptor.forClass(GraphUpdateEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/p1/updates"), captor.capture());
        GraphUpdateEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo("INCREMENTAL");
        assertThat(event.projectId()).isEqualTo("p1");
        assertThat(event.graph()).isNull();
        assertThat(event.added().nodes()).extracting(NodeDto::getId).containsExactly("n2");
        assertThat(event.added().edges()).extracting(EdgeDto::getId).containsExactly("e1");
        assertThat(event.modified().nodes()).extracting(NodeDto::getType).containsExactly("Interface");
        assertThat(event.removed().nodeIds()).containsExactly("n9");
        assertThat(event.removed().edgeIds()).containsExactly("e9");
    }

    @Test
    @DisplayName("broadcastIncremental tolerates null diff sections without error")
    void broadcastIncrementalAllowsNullSections() {
        controller.broadcastIncremental("p1", null, null, null);

        ArgumentCaptor<GraphUpdateEvent> captor = ArgumentCaptor.forClass(GraphUpdateEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/p1/updates"), captor.capture());
        GraphUpdateEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo("INCREMENTAL");
        assertThat(event.added()).isNull();
        assertThat(event.modified()).isNull();
        assertThat(event.removed()).isNull();
    }

    @Test
    @DisplayName("broadcast methods are a no-op when projectId is blank")
    void broadcastSkipsBlankProjectId() {
        controller.broadcastFullUpdate("  ", GraphDataResponse.builder().build());
        controller.broadcastIncremental(null, null, null, null);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
