package com.vibegraph.auth.dto;

import java.time.Instant;

import com.vibegraph.auth.domain.FeatureFlag;

public record FeatureFlagResponse(
        String key,
        String scope,
        String displayName,
        boolean enabled,
        String description,
        Instant updatedAt) {

    public static FeatureFlagResponse from(FeatureFlag flag) {
        return new FeatureFlagResponse(
                flag.getKey(),
                flag.getScope(),
                flag.getDisplayName(),
                flag.isEnabled(),
                flag.getDescription(),
                flag.getUpdatedAt());
    }
}
