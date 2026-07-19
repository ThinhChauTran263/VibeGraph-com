package com.vibegraph.patch.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

@DisplayName("Patch analysis scheduler")
class PatchAnalysisSchedulerTest {

    @Test
    @DisplayName("schedule analyzes pushed source and re-arms realtime watcher")
    void scheduleAnalyzesPushedSource() {
        ProjectService projectService = org.mockito.Mockito.mock(ProjectService.class);
        AnalyzeService analyzeService = org.mockito.Mockito.mock(AnalyzeService.class);
        GraphUpdateController graphUpdateController = org.mockito.Mockito.mock(GraphUpdateController.class);
        FileChangeBroadcaster fileChangeBroadcaster = org.mockito.Mockito.mock(FileChangeBroadcaster.class);
        PatchAnalysisScheduler scheduler = new PatchAnalysisScheduler(
                projectService,
                analyzeService,
                graphUpdateController,
                fileChangeBroadcaster,
                Runnable::run);
        when(projectService.getProject("p1")).thenReturn(ProjectResponse.builder()
                .id("p1")
                .name("Repo")
                .rootPath("/tmp/repo")
                .status("CREATED")
                .build());
        when(analyzeService.analyzeProject(eq("p1"), eq("Repo"), eq("/tmp/repo"), any()))
                .thenReturn(new AnalyzeService.AnalysisResult("p1", 2, 3, 4, 0));

        scheduler.schedule("p1");

        verify(projectService).markAnalyzing("p1");
        verify(projectService).markAnalyzed("p1", 2, 3, 4);
        verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZING, 0, "Analyzing pushed changes");
        verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZED, 100);
        verify(fileChangeBroadcaster).watchProject("p1", "/tmp/repo");
    }
}
