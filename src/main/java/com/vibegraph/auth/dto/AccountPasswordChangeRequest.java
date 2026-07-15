package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountPasswordChangeRequest(
        @NotBlank @Size(max = 200) String oldPassword,
        @NotBlank @Size(min = 8, max = 200) String newPassword,
        @NotBlank @Size(min = 8, max = 200) String confirmPassword) {
}
