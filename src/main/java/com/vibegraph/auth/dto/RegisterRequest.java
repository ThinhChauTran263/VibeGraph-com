package com.vibegraph.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Bean Validation runs at the controller boundary, so malformed input
 * yields 400 BEFORE the service performs any duplicate-email lookup.
 *
 * @param email       required, valid email (case-insensitive uniqueness enforced downstream)
 * @param password    required, minimum length enforced here
 * @param displayName required display name
 */
public record RegisterRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be 8-100 characters")
        String password,

        @NotBlank(message = "displayName is required")
        @Size(max = 120, message = "displayName must be at most 120 characters")
        String displayName) {
}
