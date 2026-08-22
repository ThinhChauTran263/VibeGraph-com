package com.vibegraph.auth.cli;

import java.time.Instant;

/** Safe approval status returned to the browser. */
public record CliDeviceApprovalResponse(
        String status,
        String projectId,
        String projectName,
        Instant expiresAt) {
}
