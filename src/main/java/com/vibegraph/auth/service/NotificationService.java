package com.vibegraph.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.dto.NotificationResponse;
import com.vibegraph.auth.repository.NotificationRepository;
import com.vibegraph.auth.repository.projection.NotificationViewRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final NotificationRepository notificationRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    @Transactional
    public List<NotificationResponse> list(int requestedLimit) {
        UUID userId = currentUser.id();
        Instant now = Instant.now(clock);
        notificationRepository.materializeActiveForUser(userId, targetForCurrentRole(), now);
        int limit = requestedLimit <= 0 ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
        return notificationRepository.findActiveForUser(userId, now, PageRequest.of(0, limit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(UUID notificationId) {
        return notificationRepository.findViewByIdAndUserId(notificationId, currentUser.id())
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        Instant now = Instant.now(clock);
        ensureUpdated(notificationRepository.markRead(notificationId, currentUser.id(), now));
        return get(notificationId);
    }

    @Transactional
    public NotificationResponse dismiss(UUID notificationId) {
        Instant now = Instant.now(clock);
        ensureUpdated(notificationRepository.dismiss(notificationId, currentUser.id(), now));
        return get(notificationId);
    }

    private String targetForCurrentRole() {
        return currentUser.principal().role() == Role.ADMIN ? "ADMIN" : "USER";
    }

    private void ensureUpdated(int updatedRows) {
        if (updatedRows == 0) {
            throw new IllegalArgumentException("Notification not found");
        }
    }

    private NotificationResponse toResponse(NotificationViewRow row) {
        String displayName = safeCreatorName(row.getCreatorDisplayName(), row.getCreatorEmail());
        return new NotificationResponse(
                row.getId(),
                row.getAnnouncementId(),
                row.getTitle(),
                row.getBody(),
                displayName,
                row.getCreatorDisplayName(),
                row.getCreatorEmail(),
                row.getCreatedAt(),
                row.getSeverity(),
                row.getType(),
                Boolean.TRUE.equals(row.getDismissible()),
                row.getReadAt() != null,
                row.getReadAt(),
                row.getDismissedAt());
    }

    private String safeCreatorName(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        if (email != null && !email.isBlank()) {
            return email;
        }
        return "VibeGraph";
    }
}
