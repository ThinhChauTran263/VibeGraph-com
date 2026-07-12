package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUserUpdatePlanRequest(
        @NotBlank String planCode
) {}
