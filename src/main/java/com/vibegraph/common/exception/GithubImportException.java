package com.vibegraph.common.exception;

/**
 * Thrown when GitHub tarball import fails.
 *
 * <p>Causes include:
 * <ul>
 *   <li>Repository is private or non-existent</li>
 *   <li>Repository size exceeds the configured limit</li>
 *   <li>GitHub API rate limit reached</li>
 *   <li>Network/IO error while streaming the tarball</li>
 * </ul>
 */
public class GithubImportException extends RuntimeException {

    public GithubImportException(String message) {
        super(message);
    }

    public GithubImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
