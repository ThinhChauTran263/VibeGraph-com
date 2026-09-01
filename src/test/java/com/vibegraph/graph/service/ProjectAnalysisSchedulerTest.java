package com.vibegraph.graph.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.websocket.GraphUpdateController;

@DisplayName("Project analysis scheduler")
class ProjectAnalysisSchedulerTest {

    @Test
    @DisplayName("successful analysis broadcasts the replacement graph")
    void successfulAnalysisBroadcastsReplacementGraph() {
        ProjectService projectService = org.mockito.Mockito.mock(ProjectService.class);
        AnalyzeService analyzeService = org.mockito.Mockito.mock(AnalyzeService.class);
        GraphRepository graphRepository = org.mockito.Mockito.mock(GraphRepository.class);
        GraphUpdateController graphUpdateController = org.mockito.Mockito.mock(GraphUpdateController.class);
        ProjectAnalysisScheduler scheduler = new ProjectAnalysisScheduler(
                projectService,
                analyzeService,
                graphRepository,
                graphUpdateController,
                Runnable::run);
        when(projectService.getProject("p1")).thenReturn(ProjectResponse.builder()
                .id("p1")
                .name("Repo")
                .rootPath("/tmp/repo")
                .status("CREATED")
                .build());
        when(analyzeService.analyzeProject(eq("p1"), eq("Repo"), eq("/tmp/repo"), any()))
                .thenReturn(new AnalyzeService.AnalysisResult("p1", 2, 3, 4, 0));
        GraphDataResponse graph = GraphDataResponse.builder().build();
        when(graphRepository.getFullGraph("p1")).thenReturn(graph);

        scheduler.schedule("p1");

        verify(projectService).markAnalyzing("p1");
        verify(projectService).markAnalyzed("p1", 2, 3, 4);
        verify(graphUpdateController).broadcastFullUpdate("p1", graph);
        verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZING, 0, "Analysis queued");
        verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZED, 100);
    }
}
