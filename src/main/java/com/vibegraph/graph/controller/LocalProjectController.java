package com.vibegraph.graph.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;
import com.vibegraph.graph.dto.request.LocalImportRequest;
import com.vibegraph.graph.dto.response.DirectoryListing;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.LocalImportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Local-directory onboarding endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/projects/import-local} — analyze an existing directory in place and
 *       start watching it for realtime updates.</li>
 *   <li>{@code GET /api/projects/browse?path=...} — base-confined server-side directory picker.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class LocalProjectController {

    private final LocalImportService localImportService;
    private final ProjectOwnershipRegistrar ownershipRegistrar;

    @PostMapping("/import-local")
    public ResponseEntity<ApiResponse<ProjectResponse>> importLocal(@Valid @RequestBody LocalImportRequest request) {
        // The project (and its id) is created synchronously here; only background ANALYSIS is async.
        // Record ownership synchronously before the 202 so no accepted import lacks an owner row.
        ProjectResponse project = localImportService.importLocal(request);
        ownershipRegistrar.registerLocal(project.getId(), project.getName());
        // 202: analysis runs in the background; progress streams over /topic/projects/{id}/status.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(project));
    }

    @GetMapping("/browse")
    public ResponseEntity<ApiResponse<DirectoryListing>> browse(
            @RequestParam(value = "path", required = false) String path) {
        return ResponseEntity.ok(ApiResponse.success(localImportService.browse(path)));
    }
}
