package com.vibegraph.auth.dto;

import java.time.Instant;

import com.vibegraph.auth.domain.entity.ProjectOwnership;

public record AccountProjectResponse(
        String id,
        String name,
        String sourceType,
        long sizeBytes,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountProjectResponse from(ProjectOwnership project) {
        return new AccountProjectResponse(
                project.getProjectId(),
                project.getName(),
                project.getSourceType() != null ? project.getSourceType().name() : null,
                project.getSizeBytes(),
                project.getStatus() != null ? project.getStatus().name() : null,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
