package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.graph.dto.request.GithubImportRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.importer.ArchiveExtractionResult;
import com.vibegraph.graph.importer.ArchiveExtractor;
import com.vibegraph.graph.importer.ArchiveType;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.importer.github.GitHubPreFlightService;
import com.vibegraph.graph.importer.github.GitHubRepositoryRef;
import com.vibegraph.graph.importer.github.GitHubTarballClient;
import com.vibegraph.graph.importer.github.GitHubUrlParser;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.service.TarballImportService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

import lombok.extern.slf4j.Slf4j;

/** Default implementation importing public GitHub repositories through their tarball API. */
@Service
@Slf4j
public class TarballImportServiceImpl implements TarballImportService {

    private final GitHubUrlParser urlParser;
    private final GitHubPreFlightService preFlightService;
    private final GitHubTarballClient tarballClient;
    private final ArchiveImportProperties properties;
    private final ArchiveExtractor archiveExtractor;
    private final ProjectService projectService;
    private final AnalyzeService analyzeService;
    private final GraphUpdateController graphUpdateController;
    private final FileChangeBroadcaster fileChangeBroadcaster;
    private final Executor analysisExecutor;

    public TarballImportServiceImpl(GitHubUrlParser urlParser,
                                    GitHubPreFlightService preFlightService,
                                    GitHubTarballClient tarballClient,
                                    ArchiveImportProperties properties,
                                    ArchiveExtractor archiveExtractor,
                                    ProjectService projectService,
                                    AnalyzeService analyzeService,
                                    GraphUpdateController graphUpdateController,
                                    FileChangeBroadcaster fileChangeBroadcaster,
                                    @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.urlParser = urlParser;
        this.preFlightService = preFlightService;
        this.tarballClient = tarballClient;
        this.properties = properties;
        this.archiveExtractor = archiveExtractor;
        this.projectService = projectService;
        this.analyzeService = analyzeService;
        this.graphUpdateController = graphUpdateController;
        this.fileChangeBroadcaster = fileChangeBroadcaster;
        this.analysisExecutor = analysisExecutor;
    }

    @Override
    public ProjectResponse importFromGithub(GithubImportRequest request) {
        GitHubRepositoryRef parsed = urlParser.parse(request.url());
        GitHubRepositoryRef resolved = preFlightService.validatePublicRepository(parsed);
        ImportContext ctx = prepareWorkspace(resolved);

        try {
            projectService.markAnalyzing(ctx.projectId());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZING, 0,
                    "GitHub repository imported; analysis started");
            analysisExecutor.execute(() -> analyzeInBackground(ctx));
            return projectService.getProject(ctx.projectId());
        } catch (RuntimeException e) {
            cleanup(ctx.workspace(), ctx.projectId());
            throw e;
        }
    }

    private ImportContext prepareWorkspace(GitHubRepositoryRef ref) {
        Path workspace = properties.getWorkspaceRoot().resolve("github-" + UUID.randomUUID());
        Path tarball = workspace.resolve("repo.tar.gz");
        Path source = workspace.resolve("source");
        String createdProjectId = null;

        try {
            Files.createDirectories(source);
            tarballClient.downloadTarball(ref, tarball, properties.getMaxSize().toBytes());
            ArchiveExtractionResult extraction = archiveExtractor.extract(tarball, ArchiveType.TAR_GZ, source);
            deleteRecursively(tarball);

            ProjectResponse project = projectService.createProjectFromWorkspace(ref.displayName(), extraction.extractedRoot());
            createdProjectId = project.getId();
            log.info("Imported GitHub tarball {}@{} as project {} ({} .java files)",
                    ref.displayName(), ref.ref(), project.getId(), extraction.javaFiles().size());
            return new ImportContext(workspace, project.getId(), project.getRootPath(), ref.displayName(), ref.ref(),
                    extraction.javaFiles().size());
        } catch (GithubImportException e) {
            cleanup(workspace, createdProjectId);
            throw e;
        } catch (RuntimeException e) {
            cleanup(workspace, createdProjectId);
            throw e;
        } catch (IOException e) {
            cleanup(workspace, createdProjectId);
            throw new GithubImportException("Failed to prepare GitHub import workspace: " + e.getMessage(), e);
        }
    }

    private void analyzeInBackground(ImportContext ctx) {
        try {
            AnalyzeService.AnalysisResult result = analyzeService.analyzeProject(ctx.projectId(), ctx.repository(), ctx.rootPath());
            projectService.markAnalyzed(ctx.projectId(),
                    result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZED, 100,
                    "GitHub repository analysis completed");
            fileChangeBroadcaster.watchProject(ctx.projectId(), ctx.rootPath());
            log.info("GitHub analysis complete for project {} from {}@{} ({} .java files)",
                    ctx.projectId(), ctx.repository(), ctx.ref(), ctx.javaFileCount());
        } catch (RuntimeException e) {
            projectService.markFailed(ctx.projectId(), e.getMessage());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.FAILED.name(), 0, e.getMessage());
            cleanup(ctx.workspace(), null); // keep the FAILED project visible to callers
            log.error("GitHub analysis failed for project {}: {}", ctx.projectId(), e.getMessage(), e);
        }
    }

    private record ImportContext(Path workspace, String projectId, String rootPath, String repository, String ref,
                                 int javaFileCount) {
    }

    private void cleanup(Path workspace, String projectId) {
        if (projectId != null) {
            try {
                projectService.deleteProject(projectId);
            } catch (RuntimeException ignored) {
                // best-effort cleanup; the project may already be gone
            }
        }
        deleteRecursively(workspace);
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    log.warn("Failed to delete {}: {}", p, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("Failed to clean workspace {}: {}", path, ex.getMessage());
        }
    }
}
