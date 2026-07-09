package com.vibegraph.common.exception;

/**
 * Login failed due to unknown email or wrong password. Mapped to HTTP 401
 * {@code INVALID_CREDENTIALS} by {@link GlobalExceptionHandler}. The message is deliberately
 * generic so it does not reveal whether the email exists (no user enumeration).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
