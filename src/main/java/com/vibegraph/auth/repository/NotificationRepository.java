package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.vibegraph.auth.repository.projection.NotificationViewRow;

public interface NotificationRepository {

    List<NotificationViewRow> findActiveForUser(
            UUID userId, String roleTarget, Instant now, Pageable pageable);

    Optional<NotificationViewRow> findViewByAnnouncementIdAndUserId(
            UUID announcementId, UUID userId, String roleTarget, Instant now);

    int markRead(UUID announcementId, UUID userId, String roleTarget, Instant now);

    int dismiss(UUID announcementId, UUID userId, String roleTarget, Instant now);

    int deleteDismissedBefore(Instant cutoff);
}
