package com.vibegraph.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.vibegraph.auth.dto.AdminOverviewResponse.AdminSeriesPoint;
import com.vibegraph.auth.web.JwtAuthFilter;

@Service
public class OnlineUserHistoryService {

    private static final int WINDOW_MINUTES = 10;
    private final ConcurrentNavigableMap<Instant, Long> samples = new ConcurrentSkipListMap<>();

    @Scheduled(fixedRate = 30_000)
    public void sampleCurrentUsers() {
        recordAndSnapshot(JwtAuthFilter.getActiveUsersCount(), Instant.now());
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
