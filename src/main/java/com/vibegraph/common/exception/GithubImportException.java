package com.vibegraph.common.exception;

/**
 * Thrown when GitHub tarball import fails (invalid URL, private repo, size exceeded, timeout).
 */
public class GithubImportException extends RuntimeException {
    public GithubImportException(String message) {
        super(message);
    }

    public GithubImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
