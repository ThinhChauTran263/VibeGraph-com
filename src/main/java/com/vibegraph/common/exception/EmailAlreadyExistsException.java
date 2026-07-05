package com.vibegraph.common.exception;

/**
 * Registration attempted with an email that already exists (case-insensitive).
 * Mapped to HTTP 409 {@code EMAIL_TAKEN} by {@link GlobalExceptionHandler}.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
