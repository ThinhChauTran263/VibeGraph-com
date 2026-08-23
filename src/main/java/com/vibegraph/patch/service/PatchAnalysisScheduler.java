package com.vibegraph.patch.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.service.AnalysisProgressListener;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

import lombok.extern.slf4j.Slf4j;

/**
 * Coalesces CLI patch pushes into background full analyses.
 *
 * <p>CLI watch can emit bursts; running one analysis per patch would waste CPU. This scheduler
 * keeps at most one analysis active per project and records one pending rerun if a new patch
 * lands while analysis is already running.
 */
@Service
@Slf4j
public class PatchAnalysisScheduler {

    private final ProjectService projectService;
    private final AnalyzeService analyzeService;
    private final GraphRepository graphRepository;
    private final GraphUpdateController graphUpdateController;
    private final FileChangeBroadcaster fileChangeBroadcaster;
    private final Executor analysisExecutor;
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Set<String> rerunRequested = ConcurrentHashMap.newKeySet();

    public PatchAnalysisScheduler(
            ProjectService projectService,
            AnalyzeService analyzeService,
            GraphRepository graphRepository,
            GraphUpdateController graphUpdateController,
            FileChangeBroadcaster fileChangeBroadcaster,
            @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.projectService = projectService;
        this.analyzeService = analyzeService;
        this.graphRepository = graphRepository;
        this.graphUpdateController = graphUpdateController;
        this.fileChangeBroadcaster = fileChangeBroadcaster;
        this.analysisExecutor = analysisExecutor;
    }

    public void schedule(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        if (!running.add(projectId)) {
            rerunRequested.add(projectId);
            return;
        }
        try {
            analysisExecutor.execute(() -> runLoop(projectId));
        } catch (RejectedExecutionException ex) {
            running.remove(projectId);
            log.warn("Analysis executor rejected CLI patch analysis for {}: {}", projectId, ex.getMessage());
        }
    }

    private void runLoop(String projectId) {
        try {
            do {
                rerunRequested.remove(projectId);
                analyze(projectId);
            } while (rerunRequested.remove(projectId));
        } finally {
            running.remove(projectId);
            if (rerunRequested.remove(projectId)) {
                schedule(projectId);
            }
        }
    }

    private void analyze(String projectId) {
        try {
            ProjectResponse project = projectService.getProject(projectId);
            projectService.markAnalyzing(projectId);
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZING, 0, "Analyzing pushed changes");
            AnalysisProgressListener listener = (percent, phase) -> {
                projectService.updateProgress(projectId, percent);
                graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZING, percent, phase);
            };
            AnalyzeService.AnalysisResult result = analyzeService.analyzeProject(
                    projectId, project.getName(), project.getRootPath(), listener);
            projectService.markAnalyzed(
                    projectId, result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());
            graphUpdateController.broadcastFullUpdate(projectId, graphRepository.getFullGraph(projectId));
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZED, 100);
            fileChangeBroadcaster.watchProject(projectId, project.getRootPath());
            log.info("Analyzed CLI-pushed project {} ({} files)", projectId, result.filesParsed());
        } catch (RuntimeException ex) {
            projectService.markFailed(projectId, ex.getMessage());
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.FAILED.name(), 0, ex.getMessage());
            log.warn("CLI patch analysis failed for {}: {}", projectId, ex.getMessage());
        }
    }
}
