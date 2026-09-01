package com.vibegraph.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.dto.NotificationResponse;
import com.vibegraph.auth.repository.NotificationRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.repository.projection.NotificationViewRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public List<NotificationResponse> list(int requestedLimit) {
        UUID userId = currentUser.id();
        Instant now = Instant.now(clock);
        int limit = requestedLimit <= 0 ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
        List<NotificationViewRow> rows = notificationRepository.findActiveForUser(
                userId, targetForCurrentRole(), now, PageRequest.of(0, limit));
        Map<UUID, User> creators = creators(rows);
        return rows
                .stream()
                .map(row -> toResponse(row, creatorFrom(creators, row.getCreatorUserId())))
                .toList();
    }

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public NotificationResponse get(UUID notificationId) {
        Instant now = Instant.now(clock);
        return notificationRepository.findViewByAnnouncementIdAndUserId(
                        notificationId, currentUser.id(), targetForCurrentRole(), now)
                .map(row -> toResponse(row, creator(row.getCreatorUserId())))
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    }

    @Transactional(transactionManager = "supabaseTransactionManager")
    public NotificationResponse markRead(UUID notificationId) {
        Instant now = Instant.now(clock);
        ensureUpdated(notificationRepository.markRead(
                notificationId, currentUser.id(), targetForCurrentRole(), now));
        return get(notificationId);
    }

    @Transactional(transactionManager = "supabaseTransactionManager")
    public NotificationResponse dismiss(UUID notificationId) {
        Instant now = Instant.now(clock);
        ensureUpdated(notificationRepository.dismiss(
                notificationId, currentUser.id(), targetForCurrentRole(), now));
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

    private NotificationResponse toResponse(NotificationViewRow row, User creator) {
        String creatorDisplayName = creator != null ? creator.getDisplayName() : row.getCreatorDisplayName();
        String creatorEmail = creator != null ? creator.getEmail() : row.getCreatorEmail();
        String displayName = safeCreatorName(creatorDisplayName, creatorEmail);
        return new NotificationResponse(
                row.getId(),
                row.getAnnouncementId(),
                row.getTitle(),
                row.getBody(),
                displayName,
                creatorDisplayName,
                creatorEmail,
                row.getCreatedAt(),
                row.getSeverity(),
                row.getType(),
                Boolean.TRUE.equals(row.getDismissible()),
                row.getReadAt() != null,
                row.getReadAt(),
                row.getDismissedAt());
    }

    private Map<UUID, User> creators(List<NotificationViewRow> rows) {
        var ids = rows.stream()
                .map(NotificationViewRow::getCreatorUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private User creator(UUID userId) {
        return userId == null ? null : userRepository.findById(userId).orElse(null);
    }

    private User creatorFrom(Map<UUID, User> creators, UUID userId) {
        return userId == null ? null : creators.get(userId);
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
