package com.vibegraph.patch.service;

import com.vibegraph.patch.dto.request.PatchRequest;
import com.vibegraph.patch.dto.response.PatchResult;

/**
 * Applies a batch of local file changes (creates/overwrites) and deletions to an imported
 * project's on-disk root on behalf of the CLI Local Patch flow.
 *
 * <p>Security contract — every implementation MUST:
 * <ul>
 *   <li>accept only relative POSIX paths confined to the project root (no absolute paths, Windows
 *       drive paths, backslashes, {@code ..} traversal, or symlink escape);</li>
 *   <li>refuse secret-bearing / dangerous files ({@code .env*}, {@code *.pem}, {@code *.key}, SSH
 *       keys), version-control and build output directories, archives, and binary content;</li>
 *   <li>enforce file-count and size limits;</li>
 *   <li>validate every entry <em>before</em> applying any change (fail-fast — never partial);</li>
 *   <li>never log file content, base64, secrets, or the caller's JWT.</li>
 * </ul>
 *
 * <p>Ownership/authorization is enforced by the caller (controller) before this runs.
 */
public interface LocalPatchService {

    /**
     * Validate and apply the patch.
     *
     * @param projectId tenant identifier (root resolved from the project's registered root path)
     * @param request   the changed files and deletions to apply
     * @return counts of applied changes plus whether a re-analysis is required
     * @throws com.vibegraph.patch.exception.PatchRejectedException if any entry is unsafe/invalid → 400
     * @throws com.vibegraph.common.exception.ProjectNotFoundException if the project is unknown → 404
     */
    PatchResult applyPatch(String projectId, PatchRequest request);
}
