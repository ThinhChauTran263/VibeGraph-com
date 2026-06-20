package com.vibegraph.graph.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/projects/import-local}.
 *
 * <p>Imports an existing directory on the backend host <em>in place</em> (no copy/extract),
 * analyzes it, and starts the file watcher so subsequent edits stream realtime graph updates.
 *
 * @param path absolute directory path on the backend host (validated against the allowed root)
 * @param name optional display name; falls back to a generated id when blank
 */
public record LocalImportRequest(
        @NotBlank(message = "path is required")
        @Size(max = 4096, message = "path is too long")
        String path,

        @Size(max = 200, message = "name must be at most 200 characters")
        String name
) {
}
