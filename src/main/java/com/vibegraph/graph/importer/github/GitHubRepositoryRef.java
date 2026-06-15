package com.vibegraph.graph.importer.github;

/** Stable reference to a public GitHub repository and the ref selected for import. */
public record GitHubRepositoryRef(String owner, String repo, String ref) {

    public GitHubRepositoryRef {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }
        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("repo is required");
        }
        owner = owner.trim();
        repo = repo.trim();
        ref = ref == null || ref.isBlank() ? null : ref.trim();
    }

    public GitHubRepositoryRef withRef(String resolvedRef) {
        return new GitHubRepositoryRef(owner, repo, resolvedRef);
    }

    public String displayName() {
        return owner + "/" + repo;
    }
}
