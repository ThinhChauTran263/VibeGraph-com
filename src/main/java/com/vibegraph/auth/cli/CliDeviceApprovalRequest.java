package com.vibegraph.auth.cli;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Browser approval payload; either projectId or projectName must be supplied. */
public record CliDeviceApprovalRequest(
        @NotBlank @Size(max = 128) String browserSecret,
        @NotBlank @Size(max = 20) String projectMode,
        @Size(max = 64) String projectId,
        @Size(max = 120) String projectName,
        UUID apiKeyId) {

    public CliDeviceApprovalRequest(
            String browserSecret, String projectMode, String projectId, String projectName) {
        this(browserSecret, projectMode, projectId, projectName, null);
    }
}
