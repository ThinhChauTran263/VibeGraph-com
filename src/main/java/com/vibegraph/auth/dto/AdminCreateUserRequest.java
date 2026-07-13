package com.vibegraph.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminCreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String displayName,
        @NotNull String role, // "USER" | "ADMIN"
        @NotBlank String planCode, // "FREE" | "PRO" | "TEAM" | "PRO_PLUS" | "MAX" | "ENTERPRISE"
        @NotBlank String temporaryPassword
) {}
