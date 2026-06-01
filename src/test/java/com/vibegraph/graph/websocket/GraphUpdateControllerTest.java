package com.vibegraph.graph.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

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
}
