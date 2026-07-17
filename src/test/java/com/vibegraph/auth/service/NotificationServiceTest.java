package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.dto.NotificationResponse;
import com.vibegraph.auth.repository.NotificationRepository;
import com.vibegraph.auth.repository.projection.NotificationViewRow;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");

    @Mock private NotificationRepository notificationRepository;
    @Mock private CurrentUser currentUser;
    @Mock private NotificationViewRow row;

    private NotificationService service;
    private UUID userId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        service = new NotificationService(
                notificationRepository,
                currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(currentUser.id()).thenReturn(userId);
        org.mockito.Mockito.lenient().when(currentUser.principal())
                .thenReturn(new AuthenticatedUser(userId, "user@test.local", Role.USER));
    }

    @Test
    @DisplayName("list materializes active announcements and returns newest notifications")
    void list_materializesAndReturnsNewestFirst() {
        when(notificationRepository.findActiveForUser(userId, NOW, org.springframework.data.domain.PageRequest.of(0, 50)))
                .thenReturn(List.of(row));
        when(row.getId()).thenReturn(notificationId);
        when(row.getAnnouncementId()).thenReturn(UUID.randomUUID());
        when(row.getTitle()).thenReturn("Maintenance");
        when(row.getBody()).thenReturn("Service restart");
        when(row.getType()).thenReturn("MAINTENANCE");
        when(row.getSeverity()).thenReturn("WARNING");
        when(row.getCreatorDisplayName()).thenReturn("Ops");
        when(row.getCreatorEmail()).thenReturn("ops@test.local");
        when(row.getCreatedAt()).thenReturn(NOW.minusSeconds(60));

        List<NotificationResponse> result = service.list(50);

        verify(notificationRepository).materializeActiveForUser(userId, "USER", NOW);
        assertThat(result).singleElement().satisfies(notification -> {
            assertThat(notification.id()).isEqualTo(notificationId);
            assertThat(notification.creatorName()).isEqualTo("Ops");
            assertThat(notification.read()).isFalse();
        });
    }

    @Test
    @DisplayName("markRead and dismiss update only notification rows owned by the current user")
    void stateChanges_areOwnerScopedAndPersisted() {
        when(notificationRepository.markRead(notificationId, userId, NOW)).thenReturn(1);
        when(notificationRepository.dismiss(notificationId, userId, NOW)).thenReturn(1);
        when(notificationRepository.findViewByIdAndUserId(notificationId, userId))
                .thenReturn(Optional.of(row));
        when(row.getId()).thenReturn(notificationId);
        when(row.getAnnouncementId()).thenReturn(UUID.randomUUID());
        when(row.getTitle()).thenReturn("Notice");
        when(row.getBody()).thenReturn("Body");
        when(row.getType()).thenReturn("GENERAL");
        when(row.getSeverity()).thenReturn("INFO");
        when(row.getCreatedAt()).thenReturn(NOW.minusSeconds(60));
        when(row.getReadAt()).thenReturn(NOW);
        when(row.getDismissedAt()).thenReturn(NOW);

        assertThat(service.markRead(notificationId).readAt()).isEqualTo(NOW);
        assertThat(service.dismiss(notificationId).dismissedAt()).isEqualTo(NOW);
        verify(notificationRepository).markRead(notificationId, userId, NOW);
        verify(notificationRepository).dismiss(notificationId, userId, NOW);
    }
}
