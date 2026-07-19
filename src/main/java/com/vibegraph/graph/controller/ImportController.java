package com.vibegraph.graph.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.graph.dto.request.GithubImportRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.ArchiveImportService;
import com.vibegraph.graph.service.TarballImportService;

import jakarta.validation.Valid;

/**
 * REST endpoints for importing projects from external sources.
 *
 * Endpoints:
 *   POST /api/projects/import-archive - Upload a .zip/.tar/.tar.gz/.tgz project archive (synchronous)
 *   POST /api/projects/import-github  - Stream a public GitHub repo tarball
 */
@RestController
@RequestMapping("/api/projects")
public class ImportController {

    private final TarballImportService tarballImportService;
    private final ArchiveImportService archiveImportService;

    public ImportController(TarballImportService tarballImportService,
                            ArchiveImportService archiveImportService) {
        this.tarballImportService = tarballImportService;
        this.archiveImportService = archiveImportService;
    }

    /**
     * Archive upload onboarding.
     *
     * <p>Default ({@code async=false}) is synchronous: the archive is extracted, analyzed, and the
     * resulting project returned before responding - hence {@code 200 OK}.
     *
     * <p>With {@code async=true} the archive is extracted and the project registered synchronously
     * (so archive errors still return {@code 400}), then analysis is submitted to a background
     * executor and the project is returned in {@code ANALYZING}/progress 0 - hence {@code 202
     * Accepted}. Subsequent {@code ANALYZED}/{@code FAILED} status is published over WebSocket at
     * {@code /topic/projects/{id}/status}.
     */
    @PostMapping(path = "/import-archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProjectResponse>> importArchive(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "async", defaultValue = "false") boolean async) {
        if (async) {
            // Archive is extracted + project registered synchronously (archive errors still 400);
            // only analysis is backgrounded. Record ownership before the 202 so the accepted
            // project always has an owner row.
            ProjectResponse accepted = archiveImportService.importArchiveAsync(name, file);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(accepted));
        }
        ProjectResponse response = archiveImportService.importArchive(name, file);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/import-github")
    public ResponseEntity<ApiResponse<ProjectResponse>> importGithub(
            @Valid @RequestBody GithubImportRequest request) {
        ProjectResponse response = tarballImportService.importFromGithub(request);
        // Record ownership synchronously before the 202 so no imported project lacks an owner row.
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response));
    }
}
