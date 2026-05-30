package com.vibegraph.graph.controller;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.AnalyzeService.AnalysisResult;
import com.vibegraph.graph.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final AnalyzeService analyzeService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(projectService.createProject(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(projectService.listProjects()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(projectService.getProject(id)));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<ApiResponse<AnalysisResult>> analyze(@PathVariable String id) {
        ProjectResponse project = projectService.getProject(id);
        AnalysisResult result = analyzeService.analyzeProject(id, project.getRootPath());

        // Persist analysis stats through the interface contract — no impl downcast.
        projectService.updateProjectStats(id, result.filesParsed(), result.nodesUpserted(), result.edgesUpserted());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}
