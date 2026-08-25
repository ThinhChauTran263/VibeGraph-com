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
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.service.AnalysisProgressListener;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.ArchiveImportService;
import com.vibegraph.graph.service.ImportCreditBilling;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;
import com.vibegraph.infrastructure.service.OperationTelemetryRecorder;

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
    private final GraphRepository graphRepository;
    private final ImportCreditBilling importCreditBilling;
    private OperationTelemetryRecorder telemetryRecorder;

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
                                    ConcurrentImportGuard concurrentImportGuard,
                                    GraphRepository graphRepository,
                                    ImportCreditBilling importCreditBilling) {
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
        this.graphRepository = graphRepository;
        this.importCreditBilling = importCreditBilling;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTelemetryRecorder(OperationTelemetryRecorder telemetryRecorder) {
        this.telemetryRecorder = telemetryRecorder;
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
            AnalyzeService.AnalysisResult result = analyzeService.analyzeProjectWithinOperation(
                    ctx.projectId(), ctx.name(), ctx.rootPath(), AnalysisProgressListener.NOOP);
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
        return importArchiveAsync(name, file, null);
    }

    @Override
    public ProjectResponse importArchiveAsync(String name, MultipartFile file,
            OperationTelemetryRecorder.OperationToken telemetryToken) {
        featureGateService.assertEnabled(FeatureGateService.IMPORT_ARCHIVE);
        validate(name, file);
        ConcurrentImportGuard.Lease lease = concurrentImportGuard.acquire(currentUser.id());
        ImportContext ctx = null;
        try {
            ctx = prepare(name, file, telemetryToken);
            projectService.markAnalyzing(ctx.projectId());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZING, 0);
            ProjectResponse response = projectService.getProject(ctx.projectId());
            ImportContext accepted = ctx.withLease(lease);
            analysisExecutor.execute(() -> analyzeInBackground(accepted));
            return response;
        } catch (RejectedExecutionException ex) {
            String reason = "Server is busy analyzing other projects. Please retry shortly.";
            if (ctx != null) {
                completeTelemetryFailure(telemetryToken, ex);
                projectService.markFailed(ctx.projectId(), reason);
                graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.FAILED, 0, reason);
                cleanup(ctx.workspace(), ctx.projectId());
            }
            lease.close();
            throw new ServiceBusyException(reason);
        } catch (RuntimeException e) {
            completeTelemetryFailure(telemetryToken, e);
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
        return prepare(name, file, null);
    }

    private ImportContext prepare(String name, MultipartFile file,
            OperationTelemetryRecorder.OperationToken telemetryToken) {
        validate(name, file);

        // Blocked/exhausted account check before we consume any server resources (extract, etc.).
        // The archive's own size is bounded by the server hard limit (upload filter + extractor
        // ceiling); the account quota is checked exactly, against the materialized .java bytes,
        // AFTER extraction - a 50MB archive holding 3MB of .java must not be rejected up front.
        UUID userId = currentUser.id();
        accountSettingsService.assertNotBlocked(userId);
        accountSettingsService.assertQuotaNotExceeded(userId, 1L);
        long hardLimitBytes = properties.getMaxSize().toBytes();

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
            ArchiveExtractionResult extraction = archiveExtractor.extract(uploaded, type, source,
                    hardLimitBytes);
            deleteRecursively(uploaded); // the raw archive is no longer needed once .java files are materialized

            // Quota check after extraction
            long totalSize = measureExtractedSize(source);
            accountSettingsService.assertQuotaNotExceeded(userId, totalSize);

            ProjectResponse project = projectService.createProjectFromWorkspace(name, extraction.extractedRoot());
            createdProjectId = project.getId();

            // Register ownership first to satisfy FK constraint in usage
            ownershipRegistrar.registerArchive(createdProjectId, project.getName());

            // Pre-charge by imported .java file count before the expensive analysis;
            // an exhausted balance fails fast (402) and the catch below removes the
            // partially-registered project.
            importCreditBilling.chargeUpfront(userId, ImportCreditBilling.OPERATION_IMPORT_ARCHIVE,
                    extraction.javaFiles().size(), createdProjectId);

            // Record storage usage synchronously
            projectUsageService.recordImport(createdProjectId, userId, totalSize);

            attachTelemetry(telemetryToken, project);
            return new ImportContext(workspace, project.getId(), project.getRootPath(), name,
                    extraction.javaFiles().size(), totalSize, userId, telemetryToken, null);
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
            AnalyzeService.AnalysisResult result = analyzeService.analyzeProjectWithinOperation(
                    ctx.projectId(), ctx.name(), ctx.rootPath(), listener);
            projectService.markAnalyzed(ctx.projectId(),
                    result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());
            graphUpdateController.broadcastStatus(ctx.projectId(), ProjectStatus.ANALYZED, 100);
            fileChangeBroadcaster.watchProject(ctx.projectId(), ctx.rootPath());

            log.info("Async-imported archive '{}' as project {} ({} .java files)",
                    ctx.name(), ctx.projectId(), ctx.javaFileCount());
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
                log.warn("Could not remove graph of failed archive project {}: {}",
                        ctx.projectId(), graphCleanupFailure.getMessage());
            }
            cleanup(ctx.workspace(), null); // keep the FAILED project; only remove the now-stale workspace
            log.error("Async analysis failed for project {}: {}", ctx.projectId(), e.getMessage(), e);
        } finally {
            ctx.lease().close();
        }
    }

    /** Synchronous import state handed to the background analysis task. */
    private record ImportContext(Path workspace, String projectId, String rootPath, String name,
            int javaFileCount, long totalSize, UUID userId,
            OperationTelemetryRecorder.OperationToken telemetryToken,
            ConcurrentImportGuard.Lease lease) {

        private ImportContext withLease(ConcurrentImportGuard.Lease acquiredLease) {
            return new ImportContext(workspace, projectId, rootPath, name, javaFileCount,
                    totalSize, userId, telemetryToken, acquiredLease);
        }
    }

    private void attachTelemetry(OperationTelemetryRecorder.OperationToken token,
            ProjectResponse project) {
        if (telemetryRecorder == null || token == null || project == null) return;
        try {
            telemetryRecorder.attach(token, project.getId(), project.getName());
        } catch (RuntimeException ex) {
            log.debug("Unable to attach archive telemetry token {}: {}", token.id(), ex.getMessage());
        }
    }

    private void completeTelemetry(ImportContext ctx, AnalyzeService.AnalysisResult result) {
        if (telemetryRecorder == null || ctx == null || ctx.telemetryToken() == null || result == null) return;
        try {
            telemetryRecorder.complete(ctx.telemetryToken(), result.nodesUpserted(),
                    result.edgesUpserted(), ctx.totalSize());
        } catch (RuntimeException ex) {
            log.debug("Unable to complete archive telemetry token {}: {}", ctx.telemetryToken(),
                    ex.getMessage());
        }
    }

    private void completeTelemetryFailure(OperationTelemetryRecorder.OperationToken token,
            Throwable error) {
        if (telemetryRecorder == null || token == null) return;
        try {
            telemetryRecorder.fail(token, error);
        } catch (RuntimeException ex) {
            log.debug("Unable to fail archive telemetry token {}: {}", token.id(), ex.getMessage());
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
