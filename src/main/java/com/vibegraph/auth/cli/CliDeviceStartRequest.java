package com.vibegraph.auth.cli;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request sent by a CLI before opening the browser authorization page. */
public record CliDeviceStartRequest(
        @NotBlank @Size(min = 43, max = 128) String codeChallenge,
        @Size(max = 120) String deviceName,
        @Size(max = 40) String client,
        @Size(max = 40) String intent,
        UUID preferredApiKeyId) {

    public CliDeviceStartRequest(
            String codeChallenge, String deviceName, String client, String intent) {
        this(codeChallenge, deviceName, client, intent, null);
    }
}
