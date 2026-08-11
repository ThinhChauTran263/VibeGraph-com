package com.vibegraph.common.exception;

/**
 * Sign-in refused because the failure budget for the address or the account is exhausted.
 *
 * @param retryAfterSeconds how long the caller must wait, surfaced as the {@code Retry-After} header
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        // Deliberately identical wording whether the address or the account tripped, and whether the
        // account exists at all: the response must not tell an attacker which of their guesses
        // named a real user.
        super("Too many sign-in attempts. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
