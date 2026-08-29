package com.vibegraph.common.exception;

/**
 * Thrown when a GitHub repository is re-imported while the existing project is still being
 * analyzed — starting a second refresh of the same project would race the running analysis,
 * so the request is rejected (409) until the current analysis finishes.
 */
public class ProjectRefreshInProgressException extends RuntimeException {
    public ProjectRefreshInProgressException(String repositoryName) {
        super("Repository " + repositoryName
                + " is still being analyzed. Wait for the analysis to finish, then retry to refresh it.");
    }
}
