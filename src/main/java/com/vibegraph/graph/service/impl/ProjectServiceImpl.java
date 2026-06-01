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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.service.ProjectService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final Map<String, ProjectResponse> projects = new ConcurrentHashMap<>();

    @Value("${vibegraph.projects.allowed-root:}")
    private String allowedRoot;

    @Autowired
    private ArchiveImportProperties archiveImportProperties;

    @Override
    public ProjectResponse createProject(CreateProjectRequest request) {
        String id = UUID.randomUUID().toString().substring(0, 8);
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

    private Path validateRootPath(String rawRootPath) {
        if (rawRootPath == null || rawRootPath.isBlank()) {
            throw new IllegalArgumentException("rootPath is required");
        }
        try {
            Path rootPath = Path.of(rawRootPath).toRealPath();
            if (!Files.isDirectory(rootPath)) {
                throw new IllegalArgumentException("rootPath must be an existing directory");
            }
            Path allowedRootPath = resolveAllowedRoot();
            if (allowedRootPath != null && !rootPath.startsWith(allowedRootPath)) {
                throw new IllegalArgumentException("rootPath must be inside the configured allowed root");
            }
            return rootPath;
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("rootPath is not a valid filesystem path", ex);
        } catch (IOException ex) {
            throw new IllegalArgumentException("rootPath must be an existing directory", ex);
        }
    }

    private Path resolveAllowedRoot() throws IOException {
        if (allowedRoot == null || allowedRoot.isBlank()) {
            return null;
        }
        return Path.of(allowedRoot).toRealPath();
    }

    @Override
    public ProjectResponse createProjectFromWorkspace(String name, Path workspaceSource) {
        Path source = validateWorkspacePath(workspaceSource);
        String id = UUID.randomUUID().toString().substring(0, 8);
        ProjectResponse project = ProjectResponse.builder()
                .id(id)
                .name(name != null && !name.isBlank() ? name : id)
                .rootPath(source.toString())
                .createdAt(Instant.now())
                .status(ProjectStatus.CREATED.name())
                .progress(0)
                .build();
        projects.put(id, project);
        log.info("Created archive-workspace project {} at {}", id, source);
        return project;
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
        return List.copyOf(projects.values());
    }

    @Override
    public ProjectResponse getProject(String id) {
        ProjectResponse project = projects.get(id);
        if (project == null) {
            throw new ProjectNotFoundException("Project not found: " + id);
        }
        return project;
    }

    @Override
    public void deleteProject(String id) {
        if (projects.remove(id) == null) {
            throw new ProjectNotFoundException("Project not found: " + id);
        }
        log.info("Deleted project {}", id);
    }

    @Override
    public void updateProjectStats(String id, int totalFiles, int totalNodes, int totalEdges) {
        markAnalyzed(id, totalFiles, totalNodes, totalEdges);
    }

    @Override
    public void markAnalyzing(String id) {
        ProjectResponse existing = projects.get(id);
        if (existing != null) {
            existing.setStatus(ProjectStatus.ANALYZING.name());
            existing.setProgress(0);
        }
    }

    @Override
    public void markAnalyzed(String id, int totalFiles, int totalNodes, int totalEdges) {
        ProjectResponse existing = projects.get(id);
        if (existing != null) {
            existing.setStatus(ProjectStatus.ANALYZED.name());
            existing.setTotalFiles(totalFiles);
            existing.setTotalNodes(totalNodes);
            existing.setTotalEdges(totalEdges);
            existing.setLastAnalyzedAt(Instant.now());
            existing.setProgress(100);
        }
    }

    @Override
    public void markFailed(String id, String reason) {
        ProjectResponse existing = projects.get(id);
        if (existing != null) {
            existing.setStatus(ProjectStatus.FAILED.name());
            log.warn("Project {} analysis failed: {}", id, reason);
        }
    }
}
