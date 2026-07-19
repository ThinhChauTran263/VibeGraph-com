package com.vibegraph.patch.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.web.ApiKeyRequestContext;
import com.vibegraph.auth.web.ApiKeyRequestContextAccessor;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.exception.UnauthorizedException;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.patch.dto.request.PatchRequest;
import com.vibegraph.patch.dto.response.PatchResult;
import com.vibegraph.patch.service.LocalPatchService;

import lombok.RequiredArgsConstructor;

/**
 * Local Patch endpoint — lets the CLI push local file changes into an imported
 * project's on-disk
 * root so the source tree can be kept in sync between {@code projects analyze}
 * runs.
 *
 * <p>
 * {@code POST /api/projects/{projectId}/patch} or {@code POST /api/projects/current/patch}
 *
 * <p>
 * Security: project-scoped requests require a valid JWT or API key
 * (enforced by the
 * security filter chain → 401 when absent) and asserts ownership before any
 * filesystem access
 * ({@code 403} for a non-owner, {@code 404} when no ownership row exists). All
 * path/content
 * safety, size limits, and fail-fast validation live in
 * {@link LocalPatchService}.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class LocalPatchController {
    private final LocalPatchService localPatchService;
    private final ProjectOwnershipGuard ownershipGuard;
    private final AccountSettingsService accountSettingsService;
    private final CurrentUser currentUser;
    private final ApiKeyRequestContextAccessor apiKeyContextAccessor;

    @PostMapping("/{projectId}/patch")
    public ResponseEntity<ApiResponse<PatchResult>> patch(
            @PathVariable String projectId,
            @RequestBody PatchRequest request) {
        apiKeyContextAccessor.assertProjectMatches(projectId);
        return applyPatch(projectId, request);
    }

    @PostMapping("/current/patch")
    public ResponseEntity<ApiResponse<PatchResult>> patchCurrent(@RequestBody PatchRequest request) {
        String projectId = apiKeyContextAccessor.current()
                .map(ApiKeyRequestContext::projectId)
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new UnauthorizedException("Project-bound API key required"));
        return applyPatch(projectId, request);
    }

    private ResponseEntity<ApiResponse<PatchResult>> applyPatch(String projectId, PatchRequest request) {
        ownershipGuard.assertOwner(projectId);
        UUID userId = currentUser.id();
        accountSettingsService.assertNotBlocked(userId);

        PatchResult result = localPatchService.applyPatch(projectId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
