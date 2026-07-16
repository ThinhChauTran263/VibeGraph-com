package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.abuse.ConcurrentImportGuard;
import com.vibegraph.common.exception.ArchiveImportException;
import com.vibegraph.common.exception.ArchiveImportException.Reason;
import com.vibegraph.common.exception.ServiceBusyException;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.importer.ArchiveExtractionResult;
import com.vibegraph.graph.importer.ArchiveExtractor;
import com.vibegraph.graph.importer.ArchiveType;
import com.vibegraph.graph.importer.ArchiveTypeDetector;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.service.AnalysisProgressListener;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.ArchiveImportService;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates archive upload import: detect type -> allocate a server-owned workspace
 * under {@code workspaceRoot} -> materialize {@code .java} files -> register the project ->
 * analyze. The {@code importArchive} path analyzes synchronously and returns the analyzed
 * project; {@code importArchiveAsync} registers synchronously then offloads analysis to the
 * {@code analysisExecutor}, publishing status over WebSocket. Any failure removes the
 * workspace (the synchronous path also removes the partially-registered project).
 */
@Service
@Slf4j
public class ArchiveImportServiceImpl implements ArchiveImportService {

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

    public ArchiveImportServiceImpl(ArchiveImportProperties properties,
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
                                    ConcurrentImportGuard concurrentImportGuard) {
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
    }

    @Override
    public ProjectResponse importArchive(String name, MultipartFile file) {
        featureGateService.assertEnabled(FeatureGateService.IMPORT_ARCHIVE);
        validate(name, file);
        ConcurrentImportGuard.Lease lease = concurrentImportGuard.acquire(currentUser.id());
        ImportContext ctx;
        try {
            ctx = prepare(name, file);
        } catch (RuntimeException e) {
            lease.close();
            throw e;
        }
        try {
            AnalyzeService.AnalysisResult result = analyzeService.analyzeProject(ctx.projectId(), ctx.name(), ctx.rootPath());
            projectService.updateProjectStats(ctx.projectId(),
                    result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());
            fileChangeBroadcaster.watchProject(ctx.projectId(), ctx.rootPath());

            log.info("Imported archive '{}' as project {} ({} .java files)",
                    ctx.name(), ctx.projectId(), ctx.javaFileCount());
            return projectService.getProject(ctx.projectId());
        } catch (RuntimeException e) {
            cleanup(ctx.workspace(), ctx.projectId());
            throw e;
        } finally {
            lease.close();
        }
    }

    @Override
    public ProjectResponse importArchiveAsync(String name, MultipartFile file) {
        featureGateService.assertEnabled(FeatureGateService.IMPORT_ARCHIVE);
        validate(name, file);
        ConcurrentImportGuard.Lease lease = concurrentImportGuard.acquire(currentUser.id());
        ImportContext ctx = null;
        try {
            ctx = prepare(name, file);
            projectService.markAnalyzing(ctx.projectId());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZING, 0);
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
            lease.close();
            throw e;
        }
    }

    private void validate(String name, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ArchiveImportException(Reason.MISSING_FILE, "No archive file was uploaded");
        }
        if (name == null || name.isBlank()) {
            throw new ArchiveImportException(Reason.BLANK_NAME, "Project name is required");
        }
    }

    /**
     * Synchronous preamble shared by both import paths: validate inputs, allocate a server-owned
     * workspace, materialize {@code .java} files, and register the project. Any failure here
     * removes the workspace and the partially-registered project before propagating, so archive
     * errors surface immediately to the caller even on the async path.
     */
    private ImportContext prepare(String name, MultipartFile file) {
        validate(name, file);

        // Blocked account check before we consume any server resources (extract, etc.).
        UUID userId = currentUser.id();
        accountSettingsService.assertNotBlocked(userId);

        ArchiveType type = ArchiveTypeDetector.detect(file.getOriginalFilename());
        Path workspace = properties.getWorkspaceRoot().resolve(UUID.randomUUID().toString());
        Path uploaded = workspace.resolve("upload");
        Path source = workspace.resolve("source");
        String createdProjectId = null;
        try {
            Files.createDirectories(source);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, uploaded);
            }
            ArchiveExtractionResult extraction = archiveExtractor.extract(uploaded, type, source);
            deleteRecursively(uploaded); // the raw archive is no longer needed once .java files are materialized

            // Quota check after extraction
            long totalSize = measureExtractedSize(source);
            accountSettingsService.assertQuotaNotExceeded(userId, totalSize);

            ProjectResponse project = projectService.createProjectFromWorkspace(name, extraction.extractedRoot());
            createdProjectId = project.getId();

            // Register ownership first to satisfy FK constraint in usage
            ownershipRegistrar.registerArchive(createdProjectId, project.getName());

            // Record storage usage synchronously
            projectUsageService.recordImport(createdProjectId, userId, totalSize);

            return new ImportContext(workspace, project.getId(), project.getRootPath(), name, extraction.javaFiles().size(), totalSize, userId, null);
        } catch (ArchiveImportException e) {
            cleanup(workspace, createdProjectId);
            throw e;
        } catch (IOException e) {
            cleanup(workspace, createdProjectId);
            throw new ArchiveImportException(Reason.EXTRACTION_FAILED, "Failed to import archive: " + e.getMessage());
        } catch (RuntimeException e) {
            cleanup(workspace, createdProjectId);
            throw e;
        }
    }

    /**
     * Background analysis for the async path: analyze, mark {@code ANALYZED}, broadcast progress.
     * On failure the project is marked {@code FAILED} and kept (so the caller can observe it),
     * while the workspace is removed.
     */
    private void analyzeInBackground(ImportContext ctx) {
        try {
            AnalysisProgressListener listener = (percent, phase) -> {
                projectService.updateProgress(ctx.projectId(), percent);
                graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZING, percent, phase);
            };
            AnalyzeService.AnalysisResult result =
                    analyzeService.analyzeProject(ctx.projectId(), ctx.name(), ctx.rootPath(), listener);
            projectService.markAnalyzed(ctx.projectId(),
                    result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZED, 100);
            fileChangeBroadcaster.watchProject(ctx.projectId(), ctx.rootPath());

            log.info("Async-imported archive '{}' as project {} ({} .java files)",
                    ctx.name(), ctx.projectId(), ctx.javaFileCount());
        } catch (RuntimeException e) {
            projectService.markFailed(ctx.projectId(), e.getMessage());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.FAILED.name(), 0, e.getMessage());
            cleanup(ctx.workspace(), null); // keep the FAILED project; only remove the now-stale workspace
            log.error("Async analysis failed for project {}: {}", ctx.projectId(), e.getMessage(), e);
        } finally {
            ctx.lease().close();
        }
    }

    /** Synchronous import state handed to the background analysis task. */
    private record ImportContext(Path workspace, String projectId, String rootPath, String name,
            int javaFileCount, long totalSize, UUID userId, ConcurrentImportGuard.Lease lease) {

        private ImportContext withLease(ConcurrentImportGuard.Lease acquiredLease) {
            return new ImportContext(workspace, projectId, rootPath, name, javaFileCount,
                    totalSize, userId, acquiredLease);
        }
    }

    private void cleanup(Path workspace, String projectId) {
        if (projectId != null) {
            try {
                projectService.deleteProject(projectId);
            } catch (RuntimeException ignored) {
                // best-effort - the project may already be gone
            }
            try {
                ownershipRegistrar.unregister(projectId);
            } catch (RuntimeException ex) {
                log.warn("Failed to clean archive import ownership {}: {}", projectId, ex.getMessage());
            }
        }
        deleteRecursively(workspace);
    }

    /** Delete a file or directory tree, best-effort. Only ever called on workspace paths. */
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
