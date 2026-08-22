package com.vibegraph.auth.cli;

import java.time.Instant;
import java.util.UUID;

/** Public data needed by the CLI to display and poll a browser authorization request. */
public record CliDeviceStartResponse(
        UUID requestId,
        String deviceCode,
        String userCode,
        String verificationUri,
        String verificationUriComplete,
        String pollToken,
        int intervalSeconds,
        Instant expiresAt) {
}
