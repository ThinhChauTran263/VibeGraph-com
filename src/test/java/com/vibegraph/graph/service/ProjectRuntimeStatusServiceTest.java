package com.vibegraph.graph.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.vibegraph.graph.repository.ProjectRuntimeStatusRepository;
import com.vibegraph.graph.websocket.ProjectStatusEvent;

class ProjectRuntimeStatusServiceTest {

    @Test
    void record_PersistenceFails_DoesNotBreakStatusBroadcastFlow() {
        ProjectRuntimeStatusRepository repository = mock(ProjectRuntimeStatusRepository.class);
        ProjectStatusEvent event = new ProjectStatusEvent(
                "project-1", "ANALYZING", 50, null, Instant.parse("2026-08-08T10:00:00Z"));
        doThrow(new IllegalStateException("Supabase unavailable")).when(repository).upsert(event);
        ProjectRuntimeStatusService service = new ProjectRuntimeStatusService(repository);

        assertThatCode(() -> service.record(event)).doesNotThrowAnyException();
    }
}
