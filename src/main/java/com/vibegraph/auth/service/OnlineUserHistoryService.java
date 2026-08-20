package com.vibegraph.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.vibegraph.auth.dto.AdminOverviewResponse.AdminSeriesPoint;
import com.vibegraph.auth.web.JwtAuthFilter;
import com.vibegraph.auth.websocket.OnlineUsersEvent;

@Service
public class OnlineUserHistoryService {

    /** STOMP topic the admin dashboard subscribes to for live online-user snapshots. */
    public static final String ONLINE_USERS_TOPIC = "/topic/admin/online-users";

    private static final int WINDOW_MINUTES = 10;
    private final ConcurrentNavigableMap<Instant, Long> samples = new ConcurrentSkipListMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    /** Compatibility constructor for focused unit tests without a message broker. */
    public OnlineUserHistoryService() {
        this((SimpMessagingTemplate) null);
    }

    /** Test/direct constructor with an explicit broker template. */
    public OnlineUserHistoryService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broker-aware constructor. {@link ObjectProvider} keeps contexts that scan
     * this package without WebSocket infrastructure (e.g. AdminSecurityIT) working:
     * the template is simply absent there and no push happens.
     */
    @Autowired
    public OnlineUserHistoryService(ObjectProvider<SimpMessagingTemplate> messagingTemplates) {
        this(messagingTemplates.getIfAvailable());
    }

    @Scheduled(fixedRate = 30_000)
    public void sampleCurrentUsers() {
        long onlineUsers = JwtAuthFilter.getActiveUsersCount();
        Instant capturedAt = Instant.now();
        List<AdminSeriesPoint> snapshot = recordAndSnapshot(onlineUsers, capturedAt);
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend(ONLINE_USERS_TOPIC,
                    new OnlineUsersEvent(onlineUsers, capturedAt, snapshot));
        }
    }

    public List<AdminSeriesPoint> recordAndSnapshot(long onlineUsers, Instant capturedAt) {
        samples.put(toMinute(capturedAt), onlineUsers);
        return snapshot(capturedAt);
    }

    public List<AdminSeriesPoint> snapshot(Instant now) {
        Instant cutoff = toMinute(now).minus(WINDOW_MINUTES - 1L, ChronoUnit.MINUTES);
        samples.headMap(cutoff, false).clear();
        return samples.entrySet().stream()
                .map(entry -> new AdminSeriesPoint(entry.getKey().toString(), entry.getValue(), "minute"))
                .toList();
    }

    private Instant toMinute(Instant value) {
        return value.truncatedTo(ChronoUnit.MINUTES);
    }
}
