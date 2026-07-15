package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.vibegraph.auth.domain.Announcement;

public record AnnouncementResponse(
        UUID id,
        String type,
        String severity,
        String target,
        String title,
        String body,
        Instant startsAt,
        Instant endsAt,
        boolean dismissible,
        boolean active) {

    public static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getType(),
                announcement.getSeverity(),
                announcement.getTarget(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getStartsAt(),
                announcement.getEndsAt(),
                announcement.isDismissible(),
                announcement.isActive());
    }
}
