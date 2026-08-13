package com.vibegraph.graph.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ServiceBusyException;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.websocket.GraphUpdateController;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs {@code POST /api/projects/{id}/analyze} off the request thread (H8): the endpoint
 * accepts the request with 202 and the heavy parse + Neo4j upsert happens here, with
 * progress pushed over WebSocket {@code /topic/projects/{id}/status}.
 *
 * <p>Follows the {@code PatchAnalysisScheduler} coalescing pattern: at most one analysis is
 * active per project; an analyze request landing while one is running records a single pending
 * rerun instead of queueing duplicates.
 */
@Service
@Slf4j
public class ProjectAnalysisScheduler {

    private final ProjectService projectService;
    private final AnalyzeService analyzeService;
    private final GraphUpdateController graphUpdateController;
    private final Executor analysisExecutor;
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Set<String> rerunRequested = ConcurrentHashMap.newKeySet();

    public ProjectAnalysisScheduler(
            ProjectService projectService,
            AnalyzeService analyzeService,
            GraphUpdateController graphUpdateController,
            @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.projectService = projectService;
        this.analyzeService = analyzeService;
        this.graphUpdateController = graphUpdateController;
        this.analysisExecutor = analysisExecutor;
    }

    /**
     * Queue a background analysis. A concurrent duplicate is coalesced into one pending rerun.
     *
     * @throws ServiceBusyException when the executor is saturated — the caller maps this to 503
     *         so the endpoint never blocks a Tomcat thread on a full analysis.
     */
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
            log.warn("Analysis executor rejected manual analyze for {}: {}", projectId, ex.getMessage());
            throw new ServiceBusyException("Server is busy analyzing other projects. Please retry shortly.");
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
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZING, 0, "Analysis queued");
            AnalysisProgressListener listener = (percent, phase) -> {
                projectService.updateProgress(projectId, percent);
                graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZING, percent, phase);
            };
            AnalyzeService.AnalysisResult result = analyzeService.analyzeProject(
                    projectId, project.getName(), project.getRootPath(), listener);
            projectService.markAnalyzed(
                    projectId, result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZED, 100);
            log.info("Analyzed project {} in background ({} files)", projectId, result.filesParsed());
        } catch (RuntimeException ex) {
            projectService.markFailed(projectId, ex.getMessage());
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.FAILED.name(), 0, ex.getMessage());
            log.warn("Background analysis failed for project {}: {}", projectId, ex.getMessage());
        }
    }
}
