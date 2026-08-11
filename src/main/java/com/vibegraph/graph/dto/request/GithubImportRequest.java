package com.vibegraph.graph.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload for POST /api/projects/import-github.
 * Streams a GitHub tarball directly into the parser without writing to disk.
 *
 * Example:
 *   {"url": "https://github.com/spring-projects/spring-petclinic"}
 *
 * Constraints:
 *   - Public repo only (private rejected via pre-flight check)
 *   - Repo size within the account's remaining storage quota (checked via GitHub API metadata)
 *   - URL must match GitHub HTTPS pattern
 */
public record GithubImportRequest(
        @NotBlank(message = "GitHub URL is required")
        @Pattern(
                regexp = "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?/?$",
                message = "URL must match https://github.com/{owner}/{repo}"
        )
        String url
) {
}
