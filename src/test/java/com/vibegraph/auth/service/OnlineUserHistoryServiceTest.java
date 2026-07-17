package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Online user history")
class OnlineUserHistoryServiceTest {

    @Test
    @DisplayName("replaces samples in the same minute for every admin client")
    void recordAndSnapshot_sameMinute_replacesExistingSample() {
        OnlineUserHistoryService service = new OnlineUserHistoryService();
        Instant minute = Instant.parse("2026-07-17T13:05:00Z");

        service.recordAndSnapshot(3L, minute.plusSeconds(5));
        var firstClient = service.recordAndSnapshot(5L, minute.plusSeconds(45));
        var secondClient = service.snapshot(minute.plusSeconds(50));

        assertThat(firstClient).containsExactly(
                new com.vibegraph.auth.dto.AdminOverviewResponse.AdminSeriesPoint(
                        "2026-07-17T13:05:00Z", 5L, "minute"));
        assertThat(secondClient).isEqualTo(firstClient);
    }

    @Test
    @DisplayName("keeps only the latest ten one-minute samples")
    void recordAndSnapshot_moreThanTenMinutes_keepsLatestWindow() {
        OnlineUserHistoryService service = new OnlineUserHistoryService();
        Instant start = Instant.parse("2026-07-17T13:00:00Z");

        for (int index = 0; index < 12; index++) {
            service.recordAndSnapshot(index, start.plus(index, ChronoUnit.MINUTES));
        }

        var history = service.snapshot(start.plus(11, ChronoUnit.MINUTES));

        assertThat(history).hasSize(10);
        assertThat(history.getFirst().label()).isEqualTo("2026-07-17T13:02:00Z");
        assertThat(history.getLast().label()).isEqualTo("2026-07-17T13:11:00Z");
        assertThat(history.getLast().value()).isEqualTo(11L);
    }
}
