package com.vibegraph.common.exception;

/**
 * The request could not be associated with a valid authenticated user at the service layer
 * (e.g. {@code /me} called without a resolvable principal, or the principal's user no longer
 * exists). Mapped to HTTP 401 {@code UNAUTHORIZED} by {@link GlobalExceptionHandler}.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
