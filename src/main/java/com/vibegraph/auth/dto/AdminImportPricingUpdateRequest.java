package com.vibegraph.auth.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Replaces the whole tier set of one import method. Order matters: tiers
 * must be sent with strictly ascending {@code maxFiles}; exactly the last
 * tier may use {@code null} (unlimited).
 */
public record AdminImportPricingUpdateRequest(
        @NotEmpty @Valid List<Tier> tiers) {

    public record Tier(
            @NotBlank String tierCode,
            Integer maxFiles,
            @Min(0) int credits) {
    }
}
