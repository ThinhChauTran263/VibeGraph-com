package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.abuse.ConcurrentImportGuard;
import com.vibegraph.common.exception.ServiceBusyException;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;
import com.vibegraph.graph.config.ProjectsProperties;
import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.request.LocalImportRequest;
import com.vibegraph.graph.dto.response.DirectoryListing;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.service.AnalysisProgressListener;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.DirectorySizeMeasurer;
import com.vibegraph.graph.service.LocalProjectPathValidator;
import com.vibegraph.graph.service.AnalyzeService.AnalysisResult;
import com.vibegraph.graph.service.LocalImportService;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link LocalImportService}: orchestrates
 * {@code createProject → analyze → watch} for
 * an in-place directory, and exposes a base-confined directory browser.
 */
@Service
@Slf4j
public class LocalImportServiceImpl implements LocalImportService {

    /** Directories never offered by the browser (build output, VCS, deps). */
    private static final Set<String> IGNORED_DIRS = Set.of("target", "build", "out", ".git", ".idea", ".gradle",
            "node_modules", "dist", ".vibegraph");

    private final ProjectService projectService;
    private final AnalyzeService analyzeService;
    private final FileChangeBroadcaster fileChangeBroadcaster;
    private final ProjectsProperties projectsProperties;
    private final GraphUpdateController graphUpdateController;
    private final Executor analysisExecutor;
    private final AccountSettingsService accountSettingsService;
    private final ProjectUsageService projectUsageService;
    private final CurrentUser currentUser;
    private final ProjectOwnershipRegistrar ownershipRegistrar;
    private final LocalProjectPathValidator pathValidator;
    private final DirectorySizeMeasurer directorySizeMeasurer;
    private final ConcurrentImportGuard concurrentImportGuard;
    private final FeatureGateService featureGateService;

    public LocalImportServiceImpl(ProjectService projectService,
            AnalyzeService analyzeService,
            FileChangeBroadcaster fileChangeBroadcaster,
            ProjectsProperties projectsProperties,
            GraphUpdateController graphUpdateController,
            @Qualifier("analysisExecutor") Executor analysisExecutor,
            AccountSettingsService accountSettingsService,
            ProjectUsageService projectUsageService,
            CurrentUser currentUser,
            ProjectOwnershipRegistrar ownershipRegistrar,
            LocalProjectPathValidator pathValidator,
            DirectorySizeMeasurer directorySizeMeasurer,
            ConcurrentImportGuard concurrentImportGuard,
            FeatureGateService featureGateService) {
        this.projectService = projectService;
        this.analyzeService = analyzeService;
        this.fileChangeBroadcaster = fileChangeBroadcaster;
        this.projectsProperties = projectsProperties;
        this.graphUpdateController = graphUpdateController;
        this.analysisExecutor = analysisExecutor;
        this.accountSettingsService = accountSettingsService;
        this.projectUsageService = projectUsageService;
        this.currentUser = currentUser;
        this.ownershipRegistrar = ownershipRegistrar;
        this.pathValidator = pathValidator;
        this.directorySizeMeasurer = directorySizeMeasurer;
        this.concurrentImportGuard = concurrentImportGuard;
        this.featureGateService = featureGateService;
    }

    @Override
    public ProjectResponse importLocal(LocalImportRequest request) {
        featureGateService.assertEnabled(FeatureGateService.IMPORT_LOCAL);
        // Blocked account must be checked BEFORE quota (returns ACCOUNT_BLOCKED, not QUOTA_EXCEEDED).
        UUID userId = currentUser.id();
        accountSettingsService.assertNotBlocked(userId);
        ConcurrentImportGuard.Lease lease = concurrentImportGuard.acquire(userId);

        try {
            Path validatedRoot = pathValidator.validateImportRoot(request.path());
            long totalSize = directorySizeMeasurer.measureBytes(validatedRoot);
            accountSettingsService.assertQuotaNotExceeded(userId, totalSize);

            ProjectResponse created = projectService.createProject(CreateProjectRequest.builder()
                    .name(request.name())
                    .rootPath(validatedRoot.toString())
                    .build());

            try {
                ownershipRegistrar.registerLocal(created.getId(), created.getName());
                projectUsageService.recordImport(created.getId(), userId, totalSize);
            } catch (RuntimeException ex) {
                cleanupCreatedProject(created.getId());
                throw ex;
            }

            projectService.markAnalyzing(created.getId());
            graphUpdateController.broadcastStatus(created.getId(), ProjectStatus.ANALYZING, 0);
        // Analyze off the request thread so the client gets an immediate ANALYZING
        // response and a streamed progress bar (mirrors the archive/GitHub async flow).
            ProjectResponse accepted = projectService.getProject(created.getId());
            try {
                analysisExecutor.execute(() -> analyzeInBackground(
                        created.getId(), created.getName(), created.getRootPath(), lease));
            } catch (RejectedExecutionException ex) {
                String reason = "Server is busy analyzing other projects. Please retry shortly.";
                projectService.markFailed(created.getId(), reason);
                graphUpdateController.broadcastStatus(created.getId(), ProjectStatus.FAILED, 0, reason);
                lease.close();
                throw new ServiceBusyException(reason);
            }
            return accepted;
        } catch (RuntimeException e) {
            lease.close();
            throw e;
        }
    }

    private void analyzeInBackground(String projectId, String name, String rootPath,
            ConcurrentImportGuard.Lease lease) {
        try {
            AnalysisProgressListener listener = (percent, phase) -> {
                projectService.updateProgress(projectId, percent);
                graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZING, percent, phase);
            };
            AnalysisResult result = analyzeService.analyzeProject(projectId, name, rootPath, listener);
            projectService.markAnalyzed(projectId,
                    result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZED, 100);
            // Watch the very directory we analyzed → edits there stream realtime graph
            // updates.
            fileChangeBroadcaster.watchProject(projectId, rootPath);

            log.info("Local-imported project {} from {} ({} files)",
                    projectId, rootPath, result.filesParsed());
        } catch (RuntimeException e) {
            projectService.markFailed(projectId, e.getMessage());
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.FAILED.name(), 0, e.getMessage());
            log.error("Local analysis failed for project {} ({}): {}", projectId, rootPath, e.getMessage(), e);
        } finally {
            lease.close();
        }
    }

    private void cleanupCreatedProject(String projectId) {
        try {
            projectService.deleteProject(projectId);
        } catch (RuntimeException ex) {
            log.warn("Failed to clean local import project {}: {}", projectId, ex.getMessage());
        }
        try {
            ownershipRegistrar.unregister(projectId);
        } catch (RuntimeException ex) {
            log.warn("Failed to clean local import ownership {}: {}", projectId, ex.getMessage());
        }
    }

    @Override
    public DirectoryListing browse(String path) {
        Path base = configuredBase();
        boolean isUnconfined = base == null && projectsProperties.isAllowUnconfinedBrowse();
        if (base == null && !isUnconfined) {
            throw new IllegalStateException(
                    "Directory browsing is disabled until an allowed root is configured");
        }
        String trimmed = path == null ? "" : path.trim();

        if (isUnconfined && trimmed.isEmpty()) {
            return listRoots();
        }

        Path target = trimmed.isEmpty() ? base : realDirectory(trimmed);
        if (target == null || !Files.isDirectory(target)) {
            throw new IllegalArgumentException("path must be an existing directory");
        }
        if (base != null && !target.startsWith(base)) {
            throw new IllegalArgumentException("path is outside the allowed base directory");
        }

        List<DirectoryListing.Entry> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(target)) {
            children
                    .filter(Files::isDirectory)
                    .filter(LocalImportServiceImpl::isBrowsable)
                    .sorted(Comparator.comparing(LocalImportServiceImpl::fileName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(p -> entries.add(new DirectoryListing.Entry(fileName(p), p.toString(), looksLikeJava(p))));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list directory: " + target, e);
        }

        return new DirectoryListing(target.toString(), parentOf(target, base), entries);
    }

    /**
     * Configured allowed base, or {@code null} when browsing is unconfined (no
     * allowed-root set).
     */
    private Path configuredBase() {
        String allowedRoot = projectsProperties.getAllowedRoot();
        if (allowedRoot == null || allowedRoot.isBlank()) {
            return null;
        }
        try {
            return Path.of(allowedRoot).toRealPath();
        } catch (IOException | InvalidPathException e) {
            throw new IllegalArgumentException("Configured allowed-root is not accessible: " + allowedRoot, e);
        }
    }

    private Path realDirectory(String path) {
        try {
            return Path.of(path).toRealPath();
        } catch (IOException | InvalidPathException e) {
            throw new IllegalArgumentException("path is not an accessible directory", e);
        }
    }

    /**
     * Top-level "This PC" view for unconfined browsing: the filesystem roots —
     * drive letters on
     * Windows ({@code C:\}, {@code D:\}, ...), {@code /} on Unix. Drives that are
     * not accessible
     * (empty removable/optical media) are skipped so one bad drive can't break the
     * whole listing.
     * The parent is {@code null} because there is nothing above "This PC".
     */
    private DirectoryListing listRoots() {
        List<DirectoryListing.Entry> entries = new ArrayList<>();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            try {
                if (!Files.isDirectory(root)) {
                    continue;
                }
            } catch (RuntimeException e) {
                continue;
            }
            String label = fileName(root);
            if (label.isEmpty()) {
                label = root.toString(); // "C:\" / "/"
            }
            entries.add(new DirectoryListing.Entry(label, root.toString(), false));
        }
        return new DirectoryListing("", null, entries);
    }

    /**
     * Parent path for the Up control:
     * <ul>
     * <li>Confined: {@code null} at the base; otherwise the parent (still inside
     * the base).</li>
     * <li>Unconfined: the parent path, or {@code ""} (the roots sentinel) when at a
     * drive root.</li>
     * </ul>
     */
    private String parentOf(Path target, Path base) {
        Path parent = target.getParent();
        if (base != null) {
            if (target.equals(base) || parent == null || !parent.startsWith(base)) {
                return null;
            }
            return parent.toString();
        }
        return parent == null ? "" : parent.toString();
    }

    /**
     * A sub-directory is offered only if it is not hidden (dot-prefixed) and not an
     * ignored build/VCS dir.
     */
    private static boolean isBrowsable(Path dir) {
        String name = fileName(dir);
        return !name.isEmpty() && !name.startsWith(".") && !IGNORED_DIRS.contains(name);
    }

    /**
     * Cheap best-effort hint of whether {@code dir} looks like a Java project root:
     * presence of a
     * {@code src} dir / Maven / Gradle build file, or a {@code .java} file directly
     * inside. Bounded
     * (one directory listing + a few existence checks) so it stays fast even at
     * drive-root level.
     */
    private static boolean looksLikeJava(Path dir) {
        try {
            if (Files.isDirectory(dir.resolve("src"))
                    || Files.exists(dir.resolve("pom.xml"))
                    || Files.exists(dir.resolve("build.gradle"))
                    || Files.exists(dir.resolve("build.gradle.kts"))) {
                return true;
            }
            try (Stream<Path> children = Files.list(dir)) {
                return children.anyMatch(p -> {
                    Path name = p.getFileName();
                    return name != null && name.toString().endsWith(".java");
                });
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static String fileName(Path p) {
        Path name = p.getFileName();
        return name == null ? "" : name.toString();
    }

}
