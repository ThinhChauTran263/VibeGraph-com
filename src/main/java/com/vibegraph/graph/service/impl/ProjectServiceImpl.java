package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectOwnershipStatus;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.config.ProjectsProperties;
import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.repository.ProjectMetadata;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.watcher.service.FileWatcherService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final Map<String, ProjectResponse> projects = new ConcurrentHashMap<>();

    private final String allowedRoot;
    private final ArchiveImportProperties archiveImportProperties;
    private final com.vibegraph.graph.service.LocalProjectPathValidator localProjectPathValidator;
    /**
     * Optional: lets the service recover persisted project metadata (source root) after a
     * backend restart, when the in-memory registry is empty but the Neo4j {@code Project}
     * node still exists. Null when the bean is absent (some test slices).
     */
    private final GraphRepository graphRepository;
    /** Optional: present in the running app to stop the file watcher when a project is deleted. */
    private final FileWatcherService fileWatcherService;
    /**
     * Optional: Postgres ownership plane — the source of truth for a project's name/status
     * (H6). The in-memory map stays a read cache for transient fields (progress, rootPath).
     */
    private final ProjectOwnershipRepository ownershipRepository;

    // B-L7: constructor injection; optional collaborators arrive as ObjectProviders so a
    // missing bean resolves to null at bootstrap instead of failing the context.
    public ProjectServiceImpl(
            @Value("${vibegraph.projects.allowed-root:}") String allowedRoot,
            ArchiveImportProperties archiveImportProperties,
            ObjectProvider<com.vibegraph.graph.service.LocalProjectPathValidator> localProjectPathValidator,
            ObjectProvider<GraphRepository> graphRepository,
            ObjectProvider<FileWatcherService> fileWatcherService,
            ObjectProvider<ProjectOwnershipRepository> ownershipRepository) {
        this.allowedRoot = allowedRoot;
        this.archiveImportProperties = archiveImportProperties;
        this.localProjectPathValidator = localProjectPathValidator.getIfAvailable();
        this.graphRepository = graphRepository.getIfAvailable();
        this.fileWatcherService = fileWatcherService.getIfAvailable();
        this.ownershipRepository = ownershipRepository.getIfAvailable();
    }

    @Override
    public ProjectResponse createProject(CreateProjectRequest request) {
        String id = newProjectId();
        Path rootPath = validateRootPath(request.getRootPath());
        ProjectResponse project = ProjectResponse.builder()
                .id(id)
                .name(request.getName() != null ? request.getName() : id)
                .rootPath(rootPath.toString())
                .createdAt(Instant.now())
                .status(ProjectStatus.CREATED.name())
                .progress(0)
                .build();
        projects.put(id, project);
        log.info("Created project {} at {}", id, rootPath);
        return project;
    }

    /**
     * Allocate a collision-safe project id (H7): full 128-bit UUID instead of an 8-char
     * prefix, with an explicit in-memory collision check — a duplicate can never silently
     * overwrite another project's metadata.
     */
    private String newProjectId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String id = UUID.randomUUID().toString();
            if (!projects.containsKey(id)) {
                return id;
            }
        }
        throw new IllegalStateException("Unable to allocate unique project id");
    }

    private Path validateRootPath(String rawRootPath) {
        if (localProjectPathValidator != null) {
            return localProjectPathValidator.validateImportRoot(rawRootPath);
        }
        ProjectsProperties fallbackProperties = new ProjectsProperties();
        fallbackProperties.setAllowedRoot(allowedRoot);
        fallbackProperties.setAllowUnconfinedImport(false);
        return new com.vibegraph.graph.service.LocalProjectPathValidator(fallbackProperties)
                .validateImportRoot(rawRootPath);
    }

    @Override
    public ProjectResponse createProjectFromWorkspace(String name, Path workspaceSource) {
        Path source = validateWorkspacePath(workspaceSource);
        String id = newProjectId();
        ProjectResponse project = workspaceProject(id, name, source);
        projects.put(id, project);
        log.info("Created archive-workspace project {} at {}", id, source);
        return project;
    }

    @Override
    public ProjectResponse createEmptyWorkspaceProject(String name, Path workspaceSource) {
        Path source = validateWorkspacePath(workspaceSource);
        String id = newProjectId();
        ProjectResponse project = workspaceProject(id, name, source);
        projects.put(id, project);
        if (graphRepository != null) {
            graphRepository.upsertProject(id, project.getName(), project.getRootPath());
        }
        log.info("Created CLI workspace project {} at {}", id, source);
        return project;
    }

    private ProjectResponse workspaceProject(String id, String name, Path source) {
        return ProjectResponse.builder()
                .id(id)
                .name(name != null && !name.isBlank() ? name : id)
                .rootPath(source.toString())
                .createdAt(Instant.now())
                .status(ProjectStatus.CREATED.name())
                .progress(0)
                .build();
    }

    /**
     * Validate a server-generated workspace path: it must be an existing directory that
     * resolves inside the configured archive workspace root. This deliberately bypasses the
     * user-input {@code allowed-root} check (which guards user-typed paths) while still
     * preventing a workspace from escaping the archive workspace root.
     */
    private Path validateWorkspacePath(Path workspaceSource) {
        if (workspaceSource == null) {
            throw new IllegalArgumentException("workspace path is required");
        }
        try {
            Path workspaceRoot = archiveImportProperties.getWorkspaceRoot().toRealPath();
            Path source = workspaceSource.toRealPath();
            if (!Files.isDirectory(source)) {
                throw new IllegalArgumentException("workspace must be an existing directory");
            }
            if (!source.startsWith(workspaceRoot)) {
                throw new IllegalArgumentException("workspace must be inside the configured archive workspace root");
            }
            return source;
        } catch (IOException ex) {
            throw new IllegalArgumentException("workspace must be an existing directory", ex);
        }
    }

    @Override
    public List<ProjectResponse> listProjects() {
        // In-memory entries are authoritative (freshest stats/status). After a restart the
        // registry is empty, so also surface persisted Project nodes whose recorded source
        // root still passes the allowed-workspace guard (disallowed/tampered roots are skipped).
        Map<String, ProjectResponse> merged = new java.util.LinkedHashMap<>(projects);
        if (graphRepository != null) {
            try {
                for (ProjectMetadata metadata : graphRepository.findAllProjects()) {
                    if (metadata == null || metadata.id() == null || merged.containsKey(metadata.id())) {
                        continue;
                    }
                    if (metadata.path() == null || metadata.path().isBlank()
                            || !isPersistedRootAllowed(metadata.path())) {
                        continue;
                    }
                    merged.put(metadata.id(), projectFromMetadata(metadata, metadata.id()));
                }
            } catch (RuntimeException ex) {
                log.warn("Could not load persisted projects for listing: {}", ex.getMessage());
            }
        }
        // Postgres ownership rows are authoritative for name/status (H6); the merged map keeps
        // the transient fields (progress, rootPath, stats) from the in-memory/graph sources.
        applyOwnershipTruth(merged);
        return List.copyOf(merged.values());
    }

    private void applyOwnershipTruth(Map<String, ProjectResponse> merged) {
        if (ownershipRepository == null || merged.isEmpty()) {
            return;
        }
        try {
            ownershipRepository.findAllById(merged.keySet()).stream()
                    .filter(row -> !row.isTrashed())
                    .forEach(row -> merged.computeIfPresent(row.getProjectId(),
                            (key, existing) -> withOwnershipTruth(existing, row)));
        } catch (RuntimeException ex) {
            log.warn("Could not overlay ownership status on project listing: {}", ex.getMessage());
        }
    }

    @Override
    public ProjectResponse getProject(String id) {
        ProjectResponse cached = projects.get(id);
        ProjectResponse fromDb = loadFromOwnership(id, cached);
        if (fromDb != null) {
            return fromDb;
        }
        if (cached != null) {
            return cached;
        }
        ProjectResponse persisted = loadPersisted(id);
        if (persisted != null) {
            return persisted;
        }
        throw new ProjectNotFoundException("Project not found: " + id);
    }

    /**
     * Read name/status from the Postgres ownership row (H6) and merge them over the
     * transient in-memory/graph state. Returns {@code null} when no live ownership row
     * exists (or it cannot be read) so callers fall back to the legacy resolution order.
     */
    private ProjectResponse loadFromOwnership(String id, ProjectResponse cached) {
        if (ownershipRepository == null) {
            return null;
        }
        ProjectOwnership row;
        try {
            row = ownershipRepository.findById(id).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Could not read ownership row for project {}: {}", id, ex.getMessage());
            return null;
        }
        if (row == null || row.isTrashed()) {
            return null;
        }
        ProjectResponse base = cached != null ? cached : loadPersisted(id);
        if (base == null) {
            // Ownership row without graph metadata: nothing to supply rootPath/stats from.
            return null;
        }
        return withOwnershipTruth(base, row);
    }

    private ProjectResponse withOwnershipTruth(ProjectResponse base, ProjectOwnership row) {
        ProjectStatus status = toProjectStatus(row.getStatus());
        return base.toBuilder()
                .name(row.getName())
                .status(status.name())
                .progress(status == ProjectStatus.ANALYZED ? 100 : base.getProgress())
                .storedBytes(row.getSizeBytes())
                .sourceRef(row.getSourceRef())
                .sourceBranch(row.getSourceBranch())
                .build();
    }

    private ProjectStatus toProjectStatus(ProjectOwnershipStatus status) {
        if (status == null) {
            return ProjectStatus.ANALYZING;
        }
        return switch (status) {
            case ANALYZED -> ProjectStatus.ANALYZED;
            case FAILED -> ProjectStatus.FAILED;
            case ANALYZING -> ProjectStatus.ANALYZING;
        };
    }

    /**
     * Recover a project from the persisted {@code Project} node when the in-memory registry
     * has been cleared (e.g. backend restart). The recovered source root is validated to live
     * under a configured base (archive workspace root, or the projects allowed-root when set)
     * so a tampered persisted path cannot point the source tools at arbitrary files.
     *
     * @return a reconstructed response, or {@code null} if the project is not persisted
     * @throws IllegalArgumentException if the persisted root escapes the allowed base
     */
    private ProjectResponse loadPersisted(String id) {
        if (graphRepository == null) {
            return null;
        }
        ProjectMetadata metadata;
        try {
            metadata = graphRepository.findProject(id);
        } catch (RuntimeException ex) {
            return null;
        }
        if (metadata == null || metadata.path() == null || metadata.path().isBlank()) {
            return null;
        }
        if (!isPersistedRootAllowed(metadata.path())) {
            throw new IllegalArgumentException("Persisted project root is outside the allowed workspace");
        }
        log.info("Recovered project {} from persisted graph metadata", id);
        return projectFromMetadata(metadata, id);
    }

    private ProjectResponse projectFromMetadata(ProjectMetadata metadata, String fallbackId) {
        String id = metadata.id() != null ? metadata.id() : fallbackId;
        Instant recoveredAt = Instant.now();
        Instant createdAt = metadata.createdAt() != null
                ? metadata.createdAt()
                : (metadata.lastAnalyzedAt() != null ? metadata.lastAnalyzedAt() : recoveredAt);
        Instant lastAnalyzedAt = metadata.lastAnalyzedAt() != null ? metadata.lastAnalyzedAt() : createdAt;
        return ProjectResponse.builder()
                .id(id)
                .name(metadata.name() != null ? metadata.name() : id)
                .rootPath(metadata.path())
                .createdAt(createdAt)
                .lastAnalyzedAt(lastAnalyzedAt)
                .totalFiles(metadata.totalFiles())
                .totalNodes(metadata.totalNodes())
                .totalEdges(metadata.totalEdges())
                .status(ProjectStatus.ANALYZED.name())
                .progress(100)
                .build();
    }

    private boolean isPersistedRootAllowed(String rawPath) {
        Path candidate;
        try {
            candidate = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            return false;
        }
        // The archive workspace root is always configured (defaults under java.io.tmpdir) and
        // is where GitHub/archive imports materialize sources — the common persisted case.
        if (startsWithBase(candidate, archiveImportProperties.getWorkspaceRoot())) {
            return true;
        }
        // Local createProject projects live under the optional allowed-root, when configured.
        if (allowedRoot != null && !allowedRoot.isBlank()) {
            try {
                if (startsWithBase(candidate, Path.of(allowedRoot))) {
                    return true;
                }
            } catch (InvalidPathException ignored) {
                // fall through to reject
            }
        }
        // Anything else (including a tampered persisted path) is refused.
        return false;
    }

    private boolean startsWithBase(Path candidate, Path base) {
        if (base == null) {
            return false;
        }
        return candidate.startsWith(base.toAbsolutePath().normalize());
    }

    @Override
    public void deleteProject(String id) {
        ProjectResponse removed = projects.remove(id);
        if (removed == null && loadPersisted(id) == null) {
            throw new ProjectNotFoundException("Project not found: " + id);
        }
        if (graphRepository != null) {
            graphRepository.deleteProject(id);
        }
        if (fileWatcherService != null) {
            fileWatcherService.stopWatching(id);
        }
        log.info("Deleted project {}", id);
    }

    @Override
    public void updateProjectStats(String id, int totalFiles, int totalNodes, int totalEdges) {
        markAnalyzed(id, totalFiles, totalNodes, totalEdges);
    }

    @Override
    public void markAnalyzing(String id) {
        // Replace the map value with an immutable copy (atomic via computeIfPresent) instead of
        // mutating shared fields in place, so a polling request thread always observes a consistent
        // snapshot — the background analysis thread and request threads share this object.
        projects.computeIfPresent(id, (key, existing) -> existing.toBuilder()
                .status(ProjectStatus.ANALYZING.name())
                .progress(0)
                .build());
        syncOwnershipStatus(id, ProjectOwnershipStatus.ANALYZING);
    }

    @Override
    public void updateProgress(String id, int progress) {
        int clamped = Math.max(0, Math.min(100, progress));
        projects.computeIfPresent(id, (key, existing) -> existing.toBuilder()
                .progress(clamped)
                .build());
    }

    @Override
    public void markAnalyzed(String id, int totalFiles, int totalNodes, int totalEdges) {
        projects.computeIfPresent(id, (key, existing) -> existing.toBuilder()
                .status(ProjectStatus.ANALYZED.name())
                .totalFiles(totalFiles)
                .totalNodes(totalNodes)
                .totalEdges(totalEdges)
                .lastAnalyzedAt(Instant.now())
                .progress(100)
                .build());
        syncOwnershipStatus(id, ProjectOwnershipStatus.ANALYZED);
    }

    @Override
    public void markFailed(String id, String reason) {
        ProjectResponse updated = projects.computeIfPresent(id, (key, existing) -> existing.toBuilder()
                .status(ProjectStatus.FAILED.name())
                .build());
        if (updated != null) {
            log.warn("Project {} analysis failed: {}", id, reason);
        }
        syncOwnershipStatus(id, ProjectOwnershipStatus.FAILED);
    }

    /**
     * Persist the analysis status to the Postgres ownership row so it survives restarts (H6).
     * Best-effort: a DB hiccup must not fail the analysis itself — the in-memory registry and
     * the graph stay authoritative until the next transition.
     */
    private void syncOwnershipStatus(String projectId, ProjectOwnershipStatus status) {
        if (ownershipRepository == null) {
            return;
        }
        try {
            ownershipRepository.findById(projectId).ifPresent(row -> {
                row.setStatus(status);
                ownershipRepository.save(row);
            });
        } catch (RuntimeException ex) {
            log.warn("Could not persist status {} for project {}: {}", status, projectId, ex.getMessage());
        }
    }
}
