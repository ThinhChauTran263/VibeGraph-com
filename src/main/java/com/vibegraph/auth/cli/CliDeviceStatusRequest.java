package com.vibegraph.auth.cli;

import jakarta.validation.constraints.NotBlank;

/** Polling request for a pending browser authorization. */
public record CliDeviceStatusRequest(@NotBlank String deviceCode, @NotBlank String pollToken) {
}
