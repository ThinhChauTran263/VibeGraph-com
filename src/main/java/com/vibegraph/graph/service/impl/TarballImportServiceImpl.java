package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.entity.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectOwnershipStatus;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.abuse.ConcurrentImportGuard;
import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.common.exception.ProjectRefreshInProgressException;
import com.vibegraph.common.exception.RepositoryUpToDateException;
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
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.service.AnalysisProgressListener;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.ImportCreditBilling;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.service.TarballImportService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;
import com.vibegraph.infrastructure.service.OperationTelemetryRecorder;

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
    private final GraphRepository graphRepository;
    private final ImportCreditBilling importCreditBilling;
    private final ProjectOwnershipRepository ownershipRepository;
    private OperationTelemetryRecorder telemetryRecorder;

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
            ProjectTrashService trashService,
            GraphRepository graphRepository,
            ImportCreditBilling importCreditBilling,
            ProjectOwnershipRepository ownershipRepository) {
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
        this.graphRepository = graphRepository;
        this.importCreditBilling = importCreditBilling;
        this.ownershipRepository = ownershipRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTelemetryRecorder(OperationTelemetryRecorder telemetryRecorder) {
        this.telemetryRecorder = telemetryRecorder;
    }

    @Override
    public ProjectResponse importFromGithub(GithubImportRequest request) {
        return importFromGithub(request, null);
    }

    @Override
    public ProjectResponse importFromGithub(GithubImportRequest request,
            OperationTelemetryRecorder.OperationToken telemetryToken) {
        featureGateService.assertEnabled(FeatureGateService.IMPORT_GITHUB);
        UUID userId = currentUser.id();
        accountSettingsService.assertNotBlocked(userId);

        // Parsing the URL is pure, so the repository name is known before any quota check. That
        // ordering matters: re-importing a repository is an explicit replace, and a trashed copy of
        // the same repository still occupies the owner's quota. Purging it first frees that space
        // for this import instead of letting the old copy reject its own replacement.
        GitHubRepositoryRef parsed = urlParser.parse(request.url());
        // An explicit branch selection wins over the repository default branch
        // resolved during pre-flight; blank means "import the default branch".
        if (request.branch() != null) {
            parsed = parsed.withRef(request.branch());
        }
        List<String> replaced = trashService.purgeTrashedGitHubDuplicates(userId, parsed.displayName());
        if (!replaced.isEmpty()) {
            log.info("Re-import of {} permanently removed {} trashed copy/copies: {}",
                    parsed.displayName(), replaced.size(), replaced);
        }

        accountSettingsService.assertQuotaNotExceeded(userId, 1L);
        // Server hard limit bounds the download/extraction; the account quota is checked
        // exactly against the materialized .java bytes after extraction.
        long hardLimitBytes = properties.getMaxSize().toBytes();
        ConcurrentImportGuard.Lease lease = concurrentImportGuard.acquire(userId);
        ImportContext ctx = null;
        try {
            GitHubRepositoryRef resolved = preFlightService.validatePublicRepository(parsed, hardLimitBytes);
            // A live import of the same repository is refreshed in place (new HEAD) or blocked
            // (unchanged HEAD) — it is never duplicated into a second project.
            Optional<ProjectOwnership> activeDuplicate = ownershipRepository
                    .findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNull(userId, ProjectSourceType.GITHUB,
                            parsed.displayName())
                    .stream().findFirst();
            ctx = activeDuplicate.isPresent()
                    ? prepareRefresh(activeDuplicate.get(), resolved, userId, hardLimitBytes)
                    : prepareWorkspace(resolved, userId, hardLimitBytes);
            ctx = ctx.withTelemetryToken(telemetryToken);
            attachTelemetry(ctx);
            projectService.markAnalyzing(ctx.projectId());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZING, 0,
                    ctx.refresh() ? "New commits detected; refreshing the existing project"
                            : "GitHub repository imported; analysis started");
            ProjectResponse response = projectService.getProject(ctx.projectId());
            ImportContext accepted = ctx.withLease(lease);
            analysisExecutor.execute(() -> analyzeInBackground(accepted));
            return response;
        } catch (RejectedExecutionException ex) {
            String reason = "Server is busy analyzing other projects. Please retry shortly.";
            completeTelemetryFailure(telemetryToken, ex);
            if (ctx != null) {
                projectService.markFailed(ctx.projectId(), reason);
                graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.FAILED, 0, reason);
                // A refresh must never delete the pre-existing project: drop only the temp
                // workspace. Fresh imports roll the half-registered project back entirely.
                cleanup(ctx.workspace(), ctx.refresh() ? null : ctx.projectId());
            }
            lease.close();
            throw new ServiceBusyException(reason);
        } catch (RuntimeException e) {
            completeTelemetryFailure(telemetryToken, e);
            if (ctx != null) {
                cleanup(ctx.workspace(), ctx.refresh() ? null : ctx.projectId());
            }
            lease.close();
            throw e;
        }
    }

    private ImportContext prepareWorkspace(GitHubRepositoryRef ref, UUID userId, long maxBytes) {
        Path workspace = properties.getWorkspaceRoot().resolve("github-" + UUID.randomUUID());
        Path tarball = workspace.resolve("repo.tar.gz");
        Path source = workspace.resolve("source");
        String createdProjectId = null;

        try {
            Files.createDirectories(source);
            tarballClient.downloadTarball(ref, tarball, maxBytes);
            ArchiveExtractionResult extraction = archiveExtractor.extract(tarball, ArchiveType.TAR_GZ, source,
                    maxBytes);
            deleteRecursively(tarball);

            // Quota check after extraction — we now know the exact extracted size.
            long totalSize = measureExtractedSize(source);
            accountSettingsService.assertQuotaNotExceeded(userId, totalSize);

            ProjectResponse project = projectService.createProjectFromWorkspace(ref.displayName(),
                    extraction.extractedRoot());
            createdProjectId = project.getId();

            // Register ownership first to satisfy FK constraint in usage
            ownershipRegistrar.registerGithub(createdProjectId, project.getName(), ref.commitSha(), ref.ref());

            // Pre-charge by extracted .java file count before the expensive analysis;
            // an exhausted balance fails fast (402) and the catch below removes the
            // partially-registered project.
            importCreditBilling.chargeUpfront(userId, ImportCreditBilling.OPERATION_IMPORT_GITHUB,
                    extraction.javaFiles().size(), createdProjectId);

            // Record storage usage synchronously.
            projectUsageService.recordImport(createdProjectId, userId, totalSize);

            log.info("Imported GitHub tarball {}@{} as project {} ({} .java files)",
                    ref.displayName(), ref.ref(), project.getId(), extraction.javaFiles().size());
            return new ImportContext(workspace, project.getId(), project.getRootPath(), ref.displayName(),
                    ref.ref(), extraction.javaFiles().size(), totalSize, ref.commitSha(), false);
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

    /**
     * Re-import of an already-live GitHub project: instead of creating a duplicate, the existing
     * project is refreshed in place — the fresh sources replace the old workspace contents and the
     * same project id is re-analyzed. Blocked when the stored commit SHA still matches the
     * repository HEAD (nothing changed) or when the previous analysis of that project is still
     * running.
     */
    private ImportContext prepareRefresh(ProjectOwnership existing, GitHubRepositoryRef ref, UUID userId,
            long maxBytes) {
        String projectId = existing.getProjectId();
        if (ref.commitSha() != null && ref.commitSha().equals(existing.getSourceRef())) {
            throw new RepositoryUpToDateException(ref.displayName());
        }
        if (existing.getStatus() == ProjectOwnershipStatus.ANALYZING) {
            throw new ProjectRefreshInProgressException(ref.displayName());
        }
        Path rootPath = Path.of(projectService.getProject(projectId).getRootPath());

        Path workspace = properties.getWorkspaceRoot().resolve("github-refresh-" + UUID.randomUUID());
        Path tarball = workspace.resolve("repo.tar.gz");
        Path source = workspace.resolve("source");
        boolean swapped = false;
        try {
            Files.createDirectories(source);
            tarballClient.downloadTarball(ref, tarball, maxBytes);
            ArchiveExtractionResult extraction = archiveExtractor.extract(tarball, ArchiveType.TAR_GZ, source,
                    maxBytes);
            deleteRecursively(tarball);

            // Quota check after extraction — we now know the exact extracted size.
            long totalSize = measureExtractedSize(source);
            accountSettingsService.assertQuotaNotExceeded(userId, totalSize);

            // Silence the live watcher while the tree is replaced; the background re-analysis
            // re-registers the watch on success.
            fileChangeBroadcaster.unwatch(projectId);
            swapped = true;
            replaceDirectoryContents(rootPath, extraction.extractedRoot());

            // Pre-charge by extracted .java file count before the expensive analysis; an
            // exhausted balance fails fast (402) and the swapped sources stay for a retry.
            importCreditBilling.chargeUpfront(userId, ImportCreditBilling.OPERATION_IMPORT_GITHUB,
                    extraction.javaFiles().size(), projectId);

            // Record storage usage synchronously.
            projectUsageService.recordImport(projectId, userId, totalSize);

            log.info("Refreshing GitHub project {} from {}@{} ({} .java files)",
                    projectId, ref.displayName(), ref.ref(), extraction.javaFiles().size());
            return new ImportContext(workspace, projectId, rootPath.toString(), ref.displayName(), ref.ref(),
                    extraction.javaFiles().size(), totalSize, ref.commitSha(), true);
        } catch (GithubImportException e) {
            restoreWatchIfSwapped(swapped, projectId, rootPath);
            cleanup(workspace, null);
            throw e;
        } catch (RuntimeException e) {
            restoreWatchIfSwapped(swapped, projectId, rootPath);
            cleanup(workspace, null);
            throw e;
        } catch (IOException e) {
            restoreWatchIfSwapped(swapped, projectId, rootPath);
            cleanup(workspace, null);
            throw new GithubImportException("Failed to prepare GitHub refresh workspace: " + e.getMessage(), e);
        }
    }

    private void restoreWatchIfSwapped(boolean swapped, String projectId, Path rootPath) {
        if (!swapped) {
            return;
        }
        try {
            fileChangeBroadcaster.watchProject(projectId, rootPath.toString());
        } catch (RuntimeException ex) {
            log.warn("Could not restore file watcher for project {}: {}", projectId, ex.getMessage());
        }
    }

    /**
     * Replace the contents of {@code target} with {@code newContent}, keeping the {@code target}
     * directory itself (its path is referenced by the project, the watcher and Neo4j).
     */
    private void replaceDirectoryContents(Path target, Path newContent) {
        deleteRecursively(target);
        try {
            Files.createDirectories(target);
            try (var paths = Files.walk(newContent)) {
                paths.forEach(path -> {
                    Path destination = target.resolve(newContent.relativize(path).toString());
                    try {
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(destination);
                        } else {
                            Files.createDirectories(destination.getParent());
                            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void analyzeInBackground(ImportContext ctx) {
        try {
            AnalysisProgressListener listener = (percent, phase) -> {
                projectService.updateProgress(ctx.projectId(), percent);
                graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZING, percent, phase);
            };
            AnalyzeService.AnalysisResult result = analyzeService.analyzeProjectWithinOperation(
                    ctx.projectId(), ctx.repository(), ctx.rootPath(), listener);
            projectService.markAnalyzed(ctx.projectId(),
                    result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());
            if (ctx.sourceRef() != null) {
                // Only a completed analysis advances the stored SHA — a failed refresh keeps the
                // old value so the next re-import retries instead of reporting "up to date".
                ownershipRegistrar.updateSourceRef(ctx.projectId(), ctx.sourceRef(), ctx.ref());
            }
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZED, 100,
                    "GitHub repository analysis completed");
            fileChangeBroadcaster.watchProject(ctx.projectId(), ctx.rootPath());

            log.info("GitHub analysis complete for project {} from {}@{} ({} .java files)",
                    ctx.projectId(), ctx.repository(), ctx.ref(), ctx.javaFileCount());
            completeTelemetry(ctx, result);
        } catch (RuntimeException e) {
            completeTelemetryFailure(ctx == null ? null : ctx.telemetryToken(), e);
            projectService.markFailed(ctx.projectId(), e.getMessage());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.FAILED.name(), 0, e.getMessage());
            // B-M11: the FAILED project's workspace is removed below, so any graph the
            // analysis managed to write would dangle orphaned — remove it proactively.
            try {
                graphRepository.deleteProject(ctx.projectId());
            } catch (RuntimeException graphCleanupFailure) {
                log.warn("Could not remove graph of failed GitHub project {}: {}",
                        ctx.projectId(), graphCleanupFailure.getMessage());
            }
            cleanup(ctx.workspace(), null); // keep the FAILED project visible to callers
            log.error("GitHub analysis failed for project {}: {}", ctx.projectId(), e.getMessage(), e);
        } finally {
            ctx.lease().close();
        }
    }

    private record ImportContext(Path workspace, String projectId, String rootPath, String repository, String ref,
            int javaFileCount, long totalSize, String sourceRef, boolean refresh,
            OperationTelemetryRecorder.OperationToken telemetryToken,
            ConcurrentImportGuard.Lease lease) {

        private ImportContext(Path workspace, String projectId, String rootPath, String repository, String ref,
                int javaFileCount, long totalSize, String sourceRef, boolean refresh) {
            this(workspace, projectId, rootPath, repository, ref, javaFileCount, totalSize, sourceRef,
                    refresh, null, null);
        }

        private ImportContext withTelemetryToken(
                OperationTelemetryRecorder.OperationToken operationTelemetryToken) {
            return new ImportContext(workspace, projectId, rootPath, repository, ref, javaFileCount,
                    totalSize, sourceRef, refresh, operationTelemetryToken, lease);
        }

        private ImportContext withLease(ConcurrentImportGuard.Lease acquiredLease) {
            return new ImportContext(workspace, projectId, rootPath, repository, ref, javaFileCount,
                    totalSize, sourceRef, refresh, telemetryToken, acquiredLease);
        }
    }

    private void attachTelemetry(ImportContext ctx) {
        if (telemetryRecorder == null || ctx == null || ctx.telemetryToken() == null) return;
        try {
            telemetryRecorder.attach(ctx.telemetryToken(), ctx.projectId(), ctx.repository());
        } catch (RuntimeException ex) {
            log.debug("Unable to attach GitHub telemetry token {}: {}", ctx.telemetryToken().id(),
                    ex.getMessage());
        }
    }

    private void completeTelemetry(ImportContext ctx, AnalyzeService.AnalysisResult result) {
        if (telemetryRecorder == null || ctx == null || ctx.telemetryToken() == null || result == null) return;
        try {
            telemetryRecorder.complete(ctx.telemetryToken(), result.nodesUpserted(),
                    result.edgesUpserted(), ctx.totalSize());
        } catch (RuntimeException ex) {
            log.debug("Unable to complete GitHub telemetry token {}: {}", ctx.telemetryToken(),
                    ex.getMessage());
        }
    }

    private void completeTelemetryFailure(OperationTelemetryRecorder.OperationToken token,
            Throwable error) {
        if (telemetryRecorder == null || token == null) return;
        try {
            telemetryRecorder.fail(token, error);
        } catch (RuntimeException ex) {
            log.debug("Unable to fail GitHub telemetry token {}: {}", token.id(), ex.getMessage());
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
