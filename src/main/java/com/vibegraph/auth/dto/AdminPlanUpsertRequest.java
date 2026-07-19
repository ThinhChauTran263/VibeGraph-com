package com.vibegraph.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminPlanUpsertRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,32}") String code,
        @NotBlank @Size(max = 120) String name,
        @Min(0) long storageLimitMb,
        @Min(0) @Max(10_000) int apiKeyLimit,
        @Min(0) @Max(10_000_000) int monthlyCreditLimit,
        boolean contactSalesRequired,
        boolean active,
        @Min(0) @Max(10_000) int sortOrder) {
}
