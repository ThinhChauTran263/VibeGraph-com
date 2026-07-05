package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload. Missing fields yield 400 at the controller boundary before any credential
 * check. Wrong email/password yields a generic 401 (no user enumeration).
 *
 * @param email    required
 * @param password required
 */
public record LoginRequest(

        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        String password) {
}
