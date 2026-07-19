package com.vibegraph.auth.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnnouncementRequest(
        @NotBlank @Pattern(regexp = "MAINTENANCE|PLAN_CHANGE|DISK_WARNING|CLI_UPDATE|SECURITY|GENERAL") String type,
        @NotBlank @Pattern(regexp = "INFO|WARNING|CRITICAL") String severity,
        @NotBlank @Pattern(regexp = "ALL|USER|ADMIN") String target,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 2000) String body,
        Instant startsAt,
        Instant endsAt,
        boolean dismissible,
        boolean active) {
}
