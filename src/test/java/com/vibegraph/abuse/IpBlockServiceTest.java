package com.vibegraph.abuse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AuditService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.when;

class IpBlockServiceTest {

    @Test
    void create_cidrAddress_isRejectedBeforePersistence() {
        IpBlockRepository repository = mock(IpBlockRepository.class);
        IpBlockService service = new IpBlockService(
                repository, mock(CurrentUser.class), fixedClock(), mock(AuditService.class));

        assertThatThrownBy(() -> service.create("203.0.113.0/24", "Blocked", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact IP");

        verify(repository, never()).save(any());
    }

    @Test
    void create_invalidHostname_isRejectedWithoutDnsResolution() {
        IpBlockRepository repository = mock(IpBlockRepository.class);
        IpBlockService service = new IpBlockService(
                repository, mock(CurrentUser.class), fixedClock(), mock(AuditService.class));

        assertThatThrownBy(() -> service.create("example.com", "Blocked", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void remove_existingBlock_auditsTheRemovedIpAddress() {
        IpBlockRepository repository = mock(IpBlockRepository.class);
        AuditService auditService = mock(AuditService.class);
        IpBlock block = IpBlock.builder().ipAddress("203.0.113.10").build();
        when(repository.findById(any())).thenReturn(java.util.Optional.of(block));
        IpBlockService service = new IpBlockService(repository, mock(CurrentUser.class), fixedClock(), auditService);

        service.remove(java.util.UUID.randomUUID());

        verify(repository).delete(block);
        verify(auditService).recordCurrentUser("IP_UNBLOCK", null, "IP_ADDRESS", "203.0.113.10", java.util.Map.of());
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-16T10:00:00Z"), ZoneOffset.UTC);
    }
}
