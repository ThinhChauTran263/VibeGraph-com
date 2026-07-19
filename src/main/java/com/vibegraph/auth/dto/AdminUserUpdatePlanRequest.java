package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminUserUpdatePlanRequest(
        @NotBlank
        @Pattern(regexp = "FREE|PRO|PRO_PLUS|MAX|ENTERPRISE", message = "planCode is not supported")
        String planCode
) {}
