package com.vibegraph.patch.dto.response;

import java.util.List;

/**
 * Result of a {@code POST /api/projects/{projectId}/patch} operation.
 *
 * <p>On success the request either applied all changes (fail-fast: partial application never
 * happens) or, for a dry run, reports the would-be counts without touching the filesystem.
 *
 * <p>{@code rejected} is present for forward-compatibility but is always empty on a successful
 * response — any rejected entry aborts the whole request with a {@code 400 PATCH_REJECTED} instead
 * of a partial success. It never contains file content or secrets.
 */
public record PatchResult(
        String projectId,
        int changed,
        int deleted,
        List<String> rejected,
        boolean requiresAnalyze) {
}
