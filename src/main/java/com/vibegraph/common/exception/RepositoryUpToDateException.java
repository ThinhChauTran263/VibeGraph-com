package com.vibegraph.common.exception;

/**
 * Thrown when a GitHub repository is re-imported while the stored source commit SHA already
 * matches the repository HEAD — there is nothing new to import, so the request is rejected
 * (409) instead of creating a duplicate of the existing project.
 */
public class RepositoryUpToDateException extends RuntimeException {
    public RepositoryUpToDateException(String repositoryName) {
        super("Repository " + repositoryName
                + " is already imported and up to date — no new commits to refresh.");
    }
}
