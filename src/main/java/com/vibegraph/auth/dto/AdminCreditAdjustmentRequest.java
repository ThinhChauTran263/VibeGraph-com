package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminCreditAdjustmentRequest(
        @NotNull Integer creditsDelta,
        @NotBlank String reason
) {}
