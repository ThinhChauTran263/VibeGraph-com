package com.vibegraph.common.exception;

/**
 * The caller is authenticated but not permitted to act on the target resource (e.g. a project
 * owned by a different user). Mapped to HTTP 403 {@code FORBIDDEN} by {@link GlobalExceptionHandler}
 * with a generic "Access denied" message — it must not leak project or owner details.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
