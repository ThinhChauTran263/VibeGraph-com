package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.abuse.ConcurrentImportGuard;
import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.common.exception.ServiceBusyException;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;
import com.vibegraph.common.ownership.ProjectTrashService;
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
import com.vibegraph.graph.service.AnalysisProgressListener;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.service.TarballImportService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation importing public GitHub repositories through their
 * tarball API.
 */
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
    private final AccountSettingsService accountSettingsService;
    private final ProjectUsageService projectUsageService;
    private final CurrentUser currentUser;
    private final ProjectOwnershipRegistrar ownershipRegistrar;
    private final FeatureGateService featureGateService;
    private final ConcurrentImportGuard concurrentImportGuard;
    private final ProjectTrashService trashService;

    public TarballImportServiceImpl(GitHubUrlParser urlParser,
            GitHubPreFlightService preFlightService,
            GitHubTarballClient tarballClient,
            ArchiveImportProperties properties,
            ArchiveExtractor archiveExtractor,
            ProjectService projectService,
            AnalyzeService analyzeService,
            GraphUpdateController graphUpdateController,
            FileChangeBroadcaster fileChangeBroadcaster,
            @Qualifier("analysisExecutor") Executor analysisExecutor,
            AccountSettingsService accountSettingsService,
            ProjectUsageService projectUsageService,
            CurrentUser currentUser,
            ProjectOwnershipRegistrar ownershipRegistrar,
            FeatureGateService featureGateService,
            ConcurrentImportGuard concurrentImportGuard,
            ProjectTrashService trashService) {
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
        this.accountSettingsService = accountSettingsService;
        this.projectUsageService = projectUsageService;
        this.currentUser = currentUser;
        this.ownershipRegistrar = ownershipRegistrar;
        this.featureGateService = featureGateService;
        this.concurrentImportGuard = concurrentImportGuard;
        this.trashService = trashService;
    }

    @Override
    public ProjectResponse importFromGithub(GithubImportRequest request) {
        featureGateService.assertEnabled(FeatureGateService.IMPORT_GITHUB);
        UUID userId = currentUser.id();
        accountSettingsService.assertNotBlocked(userId);

        // Parsing the URL is pure, so the repository name is known before any quota check. That
        // ordering matters: re-importing a repository is an explicit replace, and a trashed copy of
        // the same repository still occupies the owner's quota. Purging it first frees that space
        // for this import instead of letting the old copy reject its own replacement.
        GitHubRepositoryRef parsed = urlParser.parse(request.url());
        List<String> replaced = trashService.purgeTrashedGitHubDuplicates(userId, parsed.displayName());
        if (!replaced.isEmpty()) {
            log.info("Re-import of {} permanently removed {} trashed copy/copies: {}",
                    parsed.displayName(), replaced.size(), replaced);
        }

        accountSettingsService.assertQuotaNotExceeded(userId, 1L);
        long remainingQuotaBytes = accountSettingsService.quotaSnapshot(userId).remainingBytes();
        ConcurrentImportGuard.Lease lease = concurrentImportGuard.acquire(userId);
        ImportContext ctx = null;
        try {
            GitHubRepositoryRef resolved = preFlightService.validatePublicRepository(parsed, remainingQuotaBytes);
            ctx = prepareWorkspace(resolved, userId, remainingQuotaBytes);
            projectService.markAnalyzing(ctx.projectId());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZING, 0,
                    "GitHub repository imported; analysis started");
            ProjectResponse response = projectService.getProject(ctx.projectId());
            ImportContext accepted = ctx.withLease(lease);
            analysisExecutor.execute(() -> analyzeInBackground(accepted));
            return response;
        } catch (RejectedExecutionException ex) {
            String reason = "Server is busy analyzing other projects. Please retry shortly.";
            if (ctx != null) {
                projectService.markFailed(ctx.projectId(), reason);
                graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.FAILED, 0, reason);
                cleanup(ctx.workspace(), ctx.projectId());
            }
            lease.close();
            throw new ServiceBusyException(reason);
        } catch (RuntimeException e) {
            if (ctx != null) {
                cleanup(ctx.workspace(), ctx.projectId());
            }
            lease.close();
            throw e;
        }
    }

    private ImportContext prepareWorkspace(GitHubRepositoryRef ref, UUID userId, long remainingQuotaBytes) {
        Path workspace = properties.getWorkspaceRoot().resolve("github-" + UUID.randomUUID());
        Path tarball = workspace.resolve("repo.tar.gz");
        Path source = workspace.resolve("source");
        String createdProjectId = null;

        try {
            Files.createDirectories(source);
            tarballClient.downloadTarball(ref, tarball, remainingQuotaBytes);
            ArchiveExtractionResult extraction = archiveExtractor.extract(tarball, ArchiveType.TAR_GZ, source,
                    remainingQuotaBytes);
            deleteRecursively(tarball);

            // Quota check after extraction — we now know the exact extracted size.
            long totalSize = measureExtractedSize(source);
            accountSettingsService.assertQuotaNotExceeded(userId, totalSize);

            ProjectResponse project = projectService.createProjectFromWorkspace(ref.displayName(),
                    extraction.extractedRoot());
            createdProjectId = project.getId();

            // Register ownership first to satisfy FK constraint in usage
            ownershipRegistrar.registerGithub(createdProjectId, project.getName());

            // Record storage usage synchronously.
            projectUsageService.recordImport(createdProjectId, userId, totalSize);

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
            AnalysisProgressListener listener = (percent, phase) -> {
                projectService.updateProgress(ctx.projectId(), percent);
                graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZING, percent, phase);
            };
            AnalyzeService.AnalysisResult result = analyzeService.analyzeProject(ctx.projectId(), ctx.repository(),
                    ctx.rootPath(), listener);
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
        } finally {
            ctx.lease().close();
        }
    }

    private record ImportContext(Path workspace, String projectId, String rootPath, String repository, String ref,
            int javaFileCount, ConcurrentImportGuard.Lease lease) {

        private ImportContext(Path workspace, String projectId, String rootPath, String repository, String ref,
                int javaFileCount) {
            this(workspace, projectId, rootPath, repository, ref, javaFileCount, null);
        }

        private ImportContext withLease(ConcurrentImportGuard.Lease acquiredLease) {
            return new ImportContext(workspace, projectId, rootPath, repository, ref, javaFileCount, acquiredLease);
        }
    }

    private void cleanup(Path workspace, String projectId) {
        if (projectId != null) {
            try {
                projectService.deleteProject(projectId);
            } catch (RuntimeException ignored) {
                // best-effort cleanup; the project may already be gone
            }
            try {
                ownershipRegistrar.unregister(projectId);
            } catch (RuntimeException ex) {
                log.warn("Failed to clean GitHub import ownership {}: {}", projectId, ex.getMessage());
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

    /**
     * Sum the sizes of all regular files under the extracted source directory.
     * Used to verify quota before registering the project.
     */
    private long measureExtractedSize(Path dir) {
        long[] totalBytes = {0L};
        try (var walk = Files.walk(dir)) {
            walk.forEach(path -> {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attributes.isSymbolicLink()) {
                        throw new IllegalArgumentException("Symbolic links are not allowed in imported sources");
                    }
                    if (attributes.isRegularFile()) {
                        totalBytes[0] = Math.addExact(totalBytes[0], attributes.size());
                    } else if (!attributes.isDirectory()) {
                        throw new IllegalArgumentException("Unsupported file type in imported sources");
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
            return totalBytes[0];
        } catch (IOException | UncheckedIOException | ArithmeticException | SecurityException ex) {
            throw new IllegalArgumentException("Imported source size could not be measured safely", ex);
        }
    }
}
