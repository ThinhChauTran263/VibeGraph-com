package com.vibegraph.graph.dto;

import java.time.Instant;

import com.vibegraph.auth.domain.entity.ProjectOwnership;

/**
 * A project sitting in the owner's trash.
 *
 * @param purgeAt        when the retention sweep will delete this permanently
 * @param daysRemaining  whole days left before {@code purgeAt}; {@code 0} means it goes on the next
 *                       sweep, so the UI can say "today" instead of showing a negative number
 */
public record TrashedProjectResponse(
        String id,
        String name,
        String sourceType,
        long sizeBytes,
        Instant deletedAt,
        Instant purgeAt,
        long daysRemaining) {

    public static TrashedProjectResponse from(ProjectOwnership ownership, Instant purgeAt, Instant now) {
        long remaining = java.time.Duration.between(now, purgeAt).toDays();
        return new TrashedProjectResponse(
                ownership.getProjectId(),
                ownership.getName(),
                ownership.getSourceType() == null ? null : ownership.getSourceType().name(),
                ownership.getSizeBytes(),
                ownership.getDeletedAt(),
                purgeAt,
                Math.max(remaining, 0));
    }
}
