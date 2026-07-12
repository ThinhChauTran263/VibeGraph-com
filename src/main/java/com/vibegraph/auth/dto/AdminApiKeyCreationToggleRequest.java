package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotNull;

public record AdminApiKeyCreationToggleRequest(
    @NotNull(message = "disabled field is required")
    Boolean disabled
) {}
