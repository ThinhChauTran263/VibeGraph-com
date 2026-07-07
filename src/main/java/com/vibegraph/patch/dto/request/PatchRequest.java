package com.vibegraph.patch.dto.request;

import java.util.List;

/**
 * Request payload for {@code POST /api/projects/{projectId}/patch}.
 *
 * <p>Carries a batch of local file changes ({@code files}) and removals ({@code deletions}) to
 * apply to an imported project's on-disk root. All paths must be <em>relative POSIX</em> paths
 * confined to the project root; validation is fail-fast — if any entry is rejected, no file is
 * written or deleted.
 *
 * <p>When {@code dryRun} is {@code true} the request is validated and the would-be change counts
 * are reported, but nothing is written or deleted.
 */
public record PatchRequest(
        List<PatchFileChange> files,
        List<PatchDeletion> deletions,
        boolean dryRun) {

    /** A single file to create or overwrite. */
    public record PatchFileChange(
            String path,
            String contentBase64,
            String encoding) {
    }

    /** A single file to remove (only if it exists under the project root). */
    public record PatchDeletion(String path) {
    }

    /** Null-safe view of the changed files. */
    public List<PatchFileChange> safeFiles() {
        return files == null ? List.of() : files;
    }

    /** Null-safe view of the deletions. */
    public List<PatchDeletion> safeDeletions() {
        return deletions == null ? List.of() : deletions;
    }
}
