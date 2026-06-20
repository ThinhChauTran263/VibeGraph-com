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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vibegraph.graph.config.ProjectsProperties;
import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.request.LocalImportRequest;
import com.vibegraph.graph.dto.response.DirectoryListing;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.service.AnalysisProgressListener;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.AnalyzeService.AnalysisResult;
import com.vibegraph.graph.service.LocalImportService;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link LocalImportService}: orchestrates {@code createProject → analyze → watch} for
 * an in-place directory, and exposes a base-confined directory browser.
 */
@Service
@Slf4j
public class LocalImportServiceImpl implements LocalImportService {

    /** Directories never offered by the browser (build output, VCS, deps). */
    private static final Set<String> IGNORED_DIRS =
            Set.of("target", "build", "out", ".git", ".idea", ".gradle", "node_modules", "dist", ".vibegraph");

    private final ProjectService projectService;
    private final AnalyzeService analyzeService;
    private final FileChangeBroadcaster fileChangeBroadcaster;
    private final ProjectsProperties projectsProperties;
    private final GraphUpdateController graphUpdateController;
    private final Executor analysisExecutor;

    public LocalImportServiceImpl(ProjectService projectService,
                                  AnalyzeService analyzeService,
                                  FileChangeBroadcaster fileChangeBroadcaster,
                                  ProjectsProperties projectsProperties,
                                  GraphUpdateController graphUpdateController,
                                  @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.projectService = projectService;
        this.analyzeService = analyzeService;
        this.fileChangeBroadcaster = fileChangeBroadcaster;
        this.projectsProperties = projectsProperties;
        this.graphUpdateController = graphUpdateController;
        this.analysisExecutor = analysisExecutor;
    }

    @Override
    public ProjectResponse importLocal(LocalImportRequest request) {
        // createProject enforces the allowed-root guard + existence/dir checks synchronously, so a
        // bad path is rejected with 400 before we accept the import.
        ProjectResponse created = projectService.createProject(CreateProjectRequest.builder()
                .name(request.name())
                .rootPath(request.path())
                .build());

        projectService.markAnalyzing(created.getId());
        graphUpdateController.broadcastStatus(created.getId(), ProjectStatus.ANALYZING, 0);
        // Analyze off the request thread so the client gets an immediate ANALYZING response and a
        // streamed progress bar (mirrors the archive/GitHub async flow).
        analysisExecutor.execute(() ->
                analyzeInBackground(created.getId(), created.getName(), created.getRootPath()));
        return projectService.getProject(created.getId());
    }

    private void analyzeInBackground(String projectId, String name, String rootPath) {
        try {
            AnalysisProgressListener listener = (percent, phase) -> {
                projectService.updateProgress(projectId, percent);
                graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZING, percent, phase);
            };
            AnalysisResult result = analyzeService.analyzeProject(projectId, name, rootPath, listener);
            projectService.markAnalyzed(projectId,
                    result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.ANALYZED, 100);
            // Watch the very directory we analyzed → edits there stream realtime graph updates.
            fileChangeBroadcaster.watchProject(projectId, rootPath);
            log.info("Local-imported project {} from {} ({} files)", projectId, rootPath, result.filesParsed());
        } catch (RuntimeException e) {
            projectService.markFailed(projectId, e.getMessage());
            graphUpdateController.broadcastStatus(projectId, ProjectStatus.FAILED.name(), 0, e.getMessage());
            log.error("Local analysis failed for project {} ({}): {}", projectId, rootPath, e.getMessage(), e);
        }
    }

    @Override
    public DirectoryListing browse(String path) {
        Path base = configuredBase(); // null when no allowed-root is set → unconfined browsing
        String trimmed = path == null ? "" : path.trim();

        // Top level with no boundary configured: offer the filesystem roots (drives) to start from.
        if (base == null && trimmed.isEmpty()) {
            return rootsListing();
        }

        // When trimmed is empty here, base is guaranteed non-null: the unconfined-and-empty
        // case already returned the roots listing above.
        Path target = trimmed.isEmpty() ? Objects.requireNonNull(base) : realDirectory(trimmed);
        if (!Files.isDirectory(target)) {
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

    /** Configured allowed base, or {@code null} when browsing is unconfined (no allowed-root set). */
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

    /** Listing of filesystem roots (e.g. {@code C:\}, {@code D:\}) shown when browsing is unconfined. */
    private DirectoryListing rootsListing() {
        List<DirectoryListing.Entry> entries = new ArrayList<>();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            if (Files.isDirectory(root)) {
                entries.add(new DirectoryListing.Entry(root.toString(), root.toString(), false));
            }
        }
        // Empty path is the "roots" sentinel; no parent (this is the top).
        return new DirectoryListing("", null, entries);
    }

    /**
     * Parent path for the Up control:
     * <ul>
     *   <li>Confined: {@code null} at the base; otherwise the parent (still inside the base).</li>
     *   <li>Unconfined: the parent path, or {@code ""} (the roots sentinel) when at a drive root.</li>
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

    /** A sub-directory is offered only if it is not hidden (dot-prefixed) and not an ignored build/VCS dir. */
    private static boolean isBrowsable(Path dir) {
        String name = fileName(dir);
        return !name.isEmpty() && !name.startsWith(".") && !IGNORED_DIRS.contains(name);
    }

    /**
     * Cheap best-effort hint of whether {@code dir} looks like a Java project root: presence of a
     * {@code src} dir / Maven / Gradle build file, or a {@code .java} file directly inside. Bounded
     * (one directory listing + a few existence checks) so it stays fast even at drive-root level.
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
