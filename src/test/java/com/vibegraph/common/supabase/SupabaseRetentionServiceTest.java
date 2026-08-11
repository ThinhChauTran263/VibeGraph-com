package com.vibegraph.common.supabase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.vibegraph.abuse.RequestEventRepository;
import com.vibegraph.auth.repository.AnnouncementRepository;
import com.vibegraph.auth.repository.NotificationRepository;
import com.vibegraph.auth.repository.SecurityEventRepository;

class SupabaseRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

    @Test
    void cleanupExpiredData_SupabaseDisabled_DoesNotTouchPrimaryTables() {
        Fixture fixture = new Fixture(false);

        fixture.service.cleanupExpiredData();

        verifyNoInteractions(
                fixture.requestEvents,
                fixture.securityEvents,
                fixture.notifications,
                fixture.announcements);
    }

    @Test
    void cleanupExpiredData_SupabaseEnabled_UsesConfiguredRetentionWindows() {
        Fixture fixture = new Fixture(true);

        fixture.service.cleanupExpiredData();

        verify(fixture.requestEvents).deleteOccurredBefore(NOW.minusSeconds(14L * 86_400));
        verify(fixture.securityEvents).deleteCreatedBefore(NOW.minusSeconds(180L * 86_400));
        verify(fixture.notifications).deleteDismissedBefore(NOW.minusSeconds(90L * 86_400));
        verify(fixture.announcements).deleteExpiredBefore(NOW.minusSeconds(180L * 86_400));
    }

    private static final class Fixture {

        private final RequestEventRepository requestEvents = mock(RequestEventRepository.class);
        private final SecurityEventRepository securityEvents = mock(SecurityEventRepository.class);
        private final NotificationRepository notifications = mock(NotificationRepository.class);
        private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
        private final SupabaseRetentionService service;

        private Fixture(boolean enabled) {
            SupabaseProperties properties = new SupabaseProperties();
            properties.setEnabled(enabled);
            service = new SupabaseRetentionService(
                    requestEvents,
                    securityEvents,
                    notifications,
                    announcements,
                    properties,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
