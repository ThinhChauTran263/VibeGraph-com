package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeatureFlagRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9_.:-]{2,120}") String key,
        @NotBlank @Pattern(regexp = "GLOBAL|MCP_TOOL") String scope,
        @NotBlank @Size(max = 160) String displayName,
        boolean enabled,
        @Size(max = 500) String description) {
}
