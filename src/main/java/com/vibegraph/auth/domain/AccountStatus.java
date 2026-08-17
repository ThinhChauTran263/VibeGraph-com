package com.vibegraph.auth.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Derived account status shown on the admin user listing (B-M4): a single enum instead of
 * hardcoded {@code List.of("ACTIVE", ...)} literals, so admin filter validation cannot
 * drift from the statuses {@code UserResponse} actually emits.
 */
public enum AccountStatus {
    ACTIVE,
    BLOCKED,
    DEACTIVATED;

    /** Case-insensitive lookup; empty when the value is not a known status. */
    public static Optional<AccountStatus> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
