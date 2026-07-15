package com.vibegraph.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Pattern(regexp = "USER|ADMIN", message = "role must be USER or ADMIN") String role,
        @NotBlank
        @Pattern(regexp = "FREE|PRO|PRO_PLUS|MAX|ENTERPRISE", message = "planCode is not supported")
        String planCode,
        @NotBlank @Size(min = 8, max = 100) String temporaryPassword
) {}
