package com.vibegraph.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreditAdjustmentRequest(
        @NotNull @Min(-1_000_000) @Max(1_000_000) Integer creditsDelta,
        @NotBlank @Size(max = 500) String reason
) {
    @jakarta.validation.constraints.AssertTrue(message = "creditsDelta must not be zero")
    public boolean isAdjustmentNonZero() {
        return creditsDelta == null || creditsDelta != 0;
    }
}
