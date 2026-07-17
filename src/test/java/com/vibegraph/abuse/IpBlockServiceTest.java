package com.vibegraph.abuse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vibegraph.auth.CurrentUser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IpBlockServiceTest {

    @Test
    void create_cidrAddress_isRejectedBeforePersistence() {
        IpBlockRepository repository = mock(IpBlockRepository.class);
        IpBlockService service = new IpBlockService(repository, mock(CurrentUser.class), fixedClock());

        assertThatThrownBy(() -> service.create("203.0.113.0/24", "Blocked", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact IP");

        verify(repository, never()).save(any());
    }

    @Test
    void create_invalidHostname_isRejectedWithoutDnsResolution() {
        IpBlockRepository repository = mock(IpBlockRepository.class);
        IpBlockService service = new IpBlockService(repository, mock(CurrentUser.class), fixedClock());

        assertThatThrownBy(() -> service.create("example.com", "Blocked", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-16T10:00:00Z"), ZoneOffset.UTC);
    }
}
