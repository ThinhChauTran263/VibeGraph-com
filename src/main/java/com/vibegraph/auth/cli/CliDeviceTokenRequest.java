package com.vibegraph.auth.cli;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Polling exchange payload for the CLI device authorization flow. */
public record CliDeviceTokenRequest(
        @NotBlank @Size(max = 128) String deviceCode,
        @NotBlank @Size(max = 128) String pollToken,
        @NotBlank @Size(min = 43, max = 128) String codeVerifier) {
}
