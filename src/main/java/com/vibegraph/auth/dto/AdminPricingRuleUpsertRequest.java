package com.vibegraph.auth.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminPricingRuleUpsertRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,64}") String operationCode,
        @NotBlank @Size(max = 120) String displayName,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 4) BigDecimal baseCredits,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 4) BigDecimal perFileCredits,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 4) BigDecimal perMbCredits,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 4) BigDecimal per1kNodesCredits,
        @Min(0) @Max(10_000_000) int minimumCredits,
        boolean active) {
}
