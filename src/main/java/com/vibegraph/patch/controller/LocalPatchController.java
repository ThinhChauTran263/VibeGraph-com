package com.vibegraph.patch.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.patch.dto.request.PatchRequest;
import com.vibegraph.patch.dto.response.PatchResult;
import com.vibegraph.patch.service.LocalPatchService;

import lombok.RequiredArgsConstructor;

/**
 * Local Patch endpoint — lets the CLI push local file changes into an imported project's on-disk
 * root so the source tree can be kept in sync between {@code projects analyze} runs.
 *
 * <p>{@code POST /api/projects/{projectId}/patch}
 *
 * <p>Security: like every project-scoped endpoint this requires a valid JWT (enforced by the
 * security filter chain → 401 when absent) and asserts ownership before any filesystem access
 * ({@code 403} for a non-owner, {@code 404} when no ownership row exists). All path/content
 * safety, size limits, and fail-fast validation live in {@link LocalPatchService}.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/patch")
@RequiredArgsConstructor
public class LocalPatchController {

    private final LocalPatchService localPatchService;
    private final ProjectOwnershipGuard ownershipGuard;

    @PostMapping
    public ResponseEntity<ApiResponse<PatchResult>> patch(
            @PathVariable String projectId,
            @RequestBody PatchRequest request) {
        // Ownership first: no filesystem work happens for an unauthenticated (401) or non-owner (403) caller.
        ownershipGuard.assertOwner(projectId);
        PatchResult result = localPatchService.applyPatch(projectId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
