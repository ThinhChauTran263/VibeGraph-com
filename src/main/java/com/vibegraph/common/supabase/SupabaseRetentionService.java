package com.vibegraph.common.supabase;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.abuse.RequestEventRepository;
import com.vibegraph.auth.repository.AnnouncementRepository;
import com.vibegraph.auth.repository.NotificationRepository;
import com.vibegraph.auth.repository.SecurityEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupabaseRetentionService {

    private final RequestEventRepository requestEventRepository;
    private final SecurityEventRepository securityEventRepository;
    private final NotificationRepository notificationRepository;
    private final AnnouncementRepository announcementRepository;
    private final SupabaseProperties properties;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional(transactionManager = "supabaseTransactionManager")
    public void cleanupExpiredData() {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = Instant.now(clock);
        SupabaseProperties.Retention retention = properties.getRetention();
        requestEventRepository.deleteOccurredBefore(
                now.minus(retention.getRequestEventDays(), ChronoUnit.DAYS));
        securityEventRepository.deleteCreatedBefore(
                now.minus(retention.getSecurityEventDays(), ChronoUnit.DAYS));
        notificationRepository.deleteDismissedBefore(
                now.minus(retention.getDismissedNotificationDays(), ChronoUnit.DAYS));
        announcementRepository.deleteExpiredBefore(
                now.minus(retention.getExpiredAnnouncementDays(), ChronoUnit.DAYS));
    }
}
