package com.vibegraph.graph.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for POST /api/projects/import-github.
 * Streams a GitHub tarball directly into the parser without writing to disk.
 *
 * Example:
 *   {"url": "https://github.com/spring-projects/spring-petclinic", "branch": "main"}
 *
 * Constraints:
 *   - Public repo only (private rejected via pre-flight check)
 *   - Repo size within the server hard limit (checked via GitHub API metadata)
 *   - URL must match GitHub HTTPS pattern
 *   - Optional branch; when absent the repository's default branch is imported
 */
public record GithubImportRequest(
        @NotBlank(message = "GitHub URL is required")
        @Pattern(
                regexp = "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?/?$",
                message = "URL must match https://github.com/{owner}/{repo}"
        )
        String url,

        @Pattern(
                regexp = "^(?!.*(?:\\.\\.|//))[A-Za-z0-9](?:[A-Za-z0-9._/-]*[A-Za-z0-9])?$",
                message = "Branch name contains unsupported characters"
        )
        @Size(max = 100, message = "Branch name is too long")
        String branch
) {

    /** Backward-compatible payload without a branch selection. */
    public GithubImportRequest(String url) {
        this(url, null);
    }

    public GithubImportRequest {
        branch = branch == null || branch.isBlank() ? null : branch.trim();
    }
}
