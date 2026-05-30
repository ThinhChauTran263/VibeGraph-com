package com.vibegraph.common.exception;

/**
 * Thrown by an endpoint whose feature is wired but not yet implemented.
 *
 * <p>Maps to HTTP 501 Not Implemented so the API contract is honest: callers get a
 * clear "not available yet" instead of a misleading 202 Accepted or an opaque 500.
 * Kept distinct from domain errors (e.g. {@link GithubImportException}) so those still
 * map to 4xx.
 */
public class FeatureNotImplementedException extends RuntimeException {
    public FeatureNotImplementedException(String message) {
        super(message);
    }
}
