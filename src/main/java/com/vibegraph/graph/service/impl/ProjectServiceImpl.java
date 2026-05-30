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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.ProjectService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final Map<String, ProjectResponse> projects = new ConcurrentHashMap<>();

    @Value("${vibegraph.projects.allowed-root:}")
    private String allowedRoot;

    @Override
    public ProjectResponse createProject(CreateProjectRequest request) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Path rootPath = validateRootPath(request.getRootPath());
        ProjectResponse project = ProjectResponse.builder()
                .id(id)
                .name(request.getName() != null ? request.getName() : id)
                .rootPath(rootPath.toString())
                .createdAt(Instant.now())
                .status("CREATED")
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
        ProjectResponse existing = projects.get(id);
        if (existing != null) {
            ProjectResponse updated = ProjectResponse.builder()
                    .id(existing.getId())
                    .name(existing.getName())
                    .rootPath(existing.getRootPath())
                    .createdAt(existing.getCreatedAt())
                    .lastAnalyzedAt(Instant.now())
                    .totalFiles(totalFiles)
                    .totalNodes(totalNodes)
                    .totalEdges(totalEdges)
                    .status("ANALYZED")
                    .build();
            projects.put(id, updated);
        }
    }
}
