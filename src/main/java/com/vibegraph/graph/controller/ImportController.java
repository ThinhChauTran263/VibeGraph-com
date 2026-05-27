package com.vibegraph.graph.controller;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.graph.dto.request.GithubImportRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.TarballImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for importing projects from external sources (currently GitHub only).
 *
 * Endpoints:
 *   POST /api/projects/import-github  — Stream a public GitHub repo tarball
 */
@RestController
@RequestMapping("/api/projects")
public class ImportController {

    private final TarballImportService tarballImportService;

    public ImportController(TarballImportService tarballImportService) {
        this.tarballImportService = tarballImportService;
    }

    @PostMapping("/import-github")
    public ResponseEntity<ApiResponse<ProjectResponse>> importGithub(
            @Valid @RequestBody GithubImportRequest request) {
        ProjectResponse response = tarballImportService.importFromGithub(request);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response));
    }
}
