package com.vibegraph.graph.service;

import java.util.List;

import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;

/**
 * Project management service — Sprint 1 scope: register, list, get, delete.
 */
public interface ProjectService {

    ProjectResponse createProject(CreateProjectRequest request);

    List<ProjectResponse> listProjects();

    ProjectResponse getProject(String id);

    void deleteProject(String id);

    /**
     * Update analysis statistics for a project after an analysis run.
     * Part of the interface so controllers depend on the contract, not the impl.
     */
    void updateProjectStats(String id, int totalFiles, int totalNodes, int totalEdges);
}
