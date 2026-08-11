package com.vibegraph.mcp.service.impl;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.vibegraph.common.ownership.ProjectOwnershipQuery;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.repository.ProjectMetadata;
import com.vibegraph.mcp.dto.response.ProjectListResponse;
import com.vibegraph.mcp.dto.response.ProjectListResponse.ProjectInfo;
import com.vibegraph.mcp.service.ProjectDirectoryService;

import lombok.RequiredArgsConstructor;

/**
 * Lists analyzed projects scoped to the CURRENT OWNER only — the control plane
 * ({@code projects.owner_id}) is the source of truth via {@link ProjectOwnershipQuery},
 * so one tenant can never enumerate another tenant's projects. Absolute server paths
 * from {@link ProjectMetadata} are deliberately not exposed.
 */
@Service
@RequiredArgsConstructor
public class ProjectDirectoryServiceImpl implements ProjectDirectoryService {

    private final ProjectOwnershipQuery ownershipQuery;
    private final GraphRepository graphRepository;

    @Override
    public ProjectListResponse listProjects() {
        Set<String> ownedIds = new LinkedHashSet<>(ownershipQuery.ownedProjectIds());
        if (ownedIds.isEmpty()) {
            return ProjectListResponse.builder()
                    .projects(List.of())
                    .warnings(List.of())
                    .notes(List.of("No projects are registered to this account. Import and analyze a project first."))
                    .build();
        }

        List<ProjectInfo> projects;
        try {
            projects = graphRepository.findAllProjects().stream()
                    .filter(metadata -> metadata.id() != null && ownedIds.contains(metadata.id()))
                    .sorted(Comparator
                            .comparing((ProjectMetadata metadata) -> metadata.name() == null ? "" : metadata.name())
                            .thenComparing(metadata -> metadata.id() == null ? "" : metadata.id()))
                    .map(this::toProjectInfo)
                    .toList();
        } catch (RuntimeException ex) {
            return ProjectListResponse.builder()
                    .projects(List.of())
                    .warnings(List.of("Project listing is temporarily unavailable."))
                    .notes(List.of())
                    .build();
        }

        return ProjectListResponse.builder()
                .projects(projects)
                .warnings(List.of())
                .notes(List.of("Only projects owned by the caller are listed. Use a project's id as projectId in other tools."))
                .build();
    }

    private ProjectInfo toProjectInfo(ProjectMetadata metadata) {
        return ProjectInfo.builder()
                .id(metadata.id())
                .name(metadata.name())
                .analyzedAt(metadata.lastAnalyzedAt() == null ? null : metadata.lastAnalyzedAt().toString())
                .totalFiles(metadata.totalFiles())
                .totalNodes(metadata.totalNodes())
                .totalEdges(metadata.totalEdges())
                .build();
    }
}
