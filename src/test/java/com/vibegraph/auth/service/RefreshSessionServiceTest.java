package com.vibegraph.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.vibegraph.auth.config.JwtProperties;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.domain.entity.UserAccountSettings;
import com.vibegraph.auth.domain.entity.RefreshSession;
import com.vibegraph.auth.repository.RefreshSessionRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.UnauthorizedException;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Mock
    private RefreshSessionRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountSettingsService accountSettingsService;

    private JwtProperties properties;
    private RefreshSessionService service;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setRefreshExpirationMs(604_800_000L);
        service = new RefreshSessionService(
                repository,
                userRepository,
                accountSettingsService,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom());
    }

    @Test
    void issue_storesOnlyHashAndUsesSevenDayAbsoluteExpiry() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@test.local").role(Role.USER).build();
        when(repository.save(any(RefreshSession.class))).thenAnswer(invocation -> {
            RefreshSession session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });

        RefreshSessionService.SessionToken token = service.issue(user);

        ArgumentCaptor<RefreshSession> captor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(repository).save(captor.capture());
        RefreshSession stored = captor.getValue();
        assertThat(token.rawToken()).isNotBlank();
        assertThat(stored.getTokenHash()).isNotEqualTo(token.rawToken());
        assertThat(stored.getTokenHash()).hasSize(64);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plusMillis(604_800_000L));
        assertThat(token.expiresAt()).isEqualTo(stored.getExpiresAt());
    }

    @Test
    void rotate_activeToken_replacesItAndKeepsFamilyExpiry() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@test.local").role(Role.USER).build();
        RefreshSession current = RefreshSession.builder()
                .id(sessionId)
                .userId(userId)
                .familyId(familyId)
                .tokenHash(RefreshSessionService.hash("refresh-token"))
                .expiresAt(NOW.plusSeconds(600))
                .build();
        when(repository.findByTokenHashForUpdate(current.getTokenHash())).thenReturn(Optional.of(current));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.save(any(RefreshSession.class))).thenAnswer(invocation -> {
            RefreshSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            return session;
        });

        RefreshSessionService.RotatedSession rotated = service.rotate("refresh-token");

        assertThat(rotated.user()).isSameAs(user);
        assertThat(rotated.token().rawToken()).isNotEqualTo("refresh-token");
        assertThat(rotated.token().expiresAt()).isEqualTo(current.getExpiresAt());
        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(current.getRevokeReason()).isEqualTo("ROTATED");
        assertThat(current.getReplacedById()).isEqualTo(rotated.token().sessionId());
        verify(repository, never()).revokeFamily(eq(familyId), any(), anyString());
    }

    @Test
    void rotate_replayedRotatedToken_revokesFamilyAndRejects() {
        UUID familyId = UUID.randomUUID();
        RefreshSession replayed = RefreshSession.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .familyId(familyId)
                .tokenHash(RefreshSessionService.hash("replayed"))
                .expiresAt(NOW.plusSeconds(600))
                .revokedAt(NOW.minusSeconds(1))
                .revokeReason("ROTATED")
                .build();
        when(repository.findByTokenHashForUpdate(replayed.getTokenHash())).thenReturn(Optional.of(replayed));

        assertThatThrownBy(() -> service.rotate("replayed"))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeFamily(eq(familyId), eq(NOW), eq("REUSE_DETECTED"));
        verifyNoInteractions(userRepository, accountSettingsService);
    }

    @Test
    void rotate_expiredToken_revokesFamilyAndRejects() {
        UUID familyId = UUID.randomUUID();
        RefreshSession expired = RefreshSession.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .familyId(familyId)
                .tokenHash(RefreshSessionService.hash("expired"))
                .expiresAt(NOW.minusSeconds(1))
                .build();
        when(repository.findByTokenHashForUpdate(expired.getTokenHash())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("expired"))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeFamily(eq(familyId), eq(NOW), eq("EXPIRED"));
        verifyNoInteractions(userRepository, accountSettingsService);
    }

    @Test
    void rotate_deactivatedAccount_revokesFamilyAndRejects() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshSession session = RefreshSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .familyId(familyId)
                .tokenHash(RefreshSessionService.hash("deactivated"))
                .expiresAt(NOW.plusSeconds(600))
                .build();
        User user = User.builder().id(userId).email("closed@test.local").deactivated(true).build();
        when(repository.findByTokenHashForUpdate(session.getTokenHash())).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.rotate("deactivated"))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeFamily(eq(familyId), eq(NOW), eq("SECURITY_EVENT"));
        verifyNoInteractions(accountSettingsService);
    }

    /**
     * Builds a token that a concurrent refresh just rotated, together with the live replacement the
     * winning caller received.
     */
    private RefreshSession rotatedWithLiveReplacement(UUID userId, UUID familyId, Instant revokedAt) {
        RefreshSession replacement = RefreshSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .familyId(familyId)
                .tokenHash(RefreshSessionService.hash("winner"))
                .expiresAt(NOW.plusSeconds(600))
                .build();
        RefreshSession loser = RefreshSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .familyId(familyId)
                .tokenHash(RefreshSessionService.hash("loser"))
                .expiresAt(NOW.plusSeconds(600))
                .revokedAt(revokedAt)
                .revokeReason("ROTATED")
                .replacedById(replacement.getId())
                .build();
        when(repository.findByTokenHashForUpdate(loser.getTokenHash())).thenReturn(Optional.of(loser));
        lenient().when(repository.findById(replacement.getId())).thenReturn(Optional.of(replacement));
        return loser;
    }

    private User activeUser(UUID userId) {
        User user = User.builder().id(userId).email("user@test.local").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(repository.save(any(RefreshSession.class))).thenAnswer(invocation -> {
            RefreshSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            return session;
        });
        return user;
    }

    @Test
    void rotate_concurrentRefreshWithinGrace_issuesSiblingInsteadOfRevokingFamily() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        rotatedWithLiveReplacement(userId, familyId, NOW.minusSeconds(5));
        activeUser(userId);

        RefreshSessionService.RotatedSession rotated = service.rotate("loser");

        // Two tabs polling on the same timer must both stay signed in.
        assertThat(rotated.token().rawToken()).isNotBlank();
        assertThat(rotated.token().expiresAt()).isEqualTo(NOW.plusSeconds(600));
        verify(repository, never()).revokeFamily(any(), any(), eq("REUSE_DETECTED"));
    }

    @Test
    void rotate_replayAfterGraceWindow_stillRevokesFamily() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        rotatedWithLiveReplacement(userId, familyId, NOW.minusSeconds(120));

        assertThatThrownBy(() -> service.rotate("loser"))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeFamily(eq(familyId), eq(NOW), eq("REUSE_DETECTED"));
    }

    @Test
    void rotate_withinGraceButReplacementAlreadyRevoked_revokesFamily() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshSession loser = rotatedWithLiveReplacement(userId, familyId, NOW.minusSeconds(5));
        RefreshSession revokedReplacement = RefreshSession.builder()
                .id(loser.getReplacedById())
                .userId(userId)
                .familyId(familyId)
                .tokenHash(RefreshSessionService.hash("winner"))
                .expiresAt(NOW.plusSeconds(600))
                .revokedAt(NOW.minusSeconds(1))
                .revokeReason("LOGOUT")
                .build();
        when(repository.findById(loser.getReplacedById())).thenReturn(Optional.of(revokedReplacement));

        // The grace window only covers a live concurrent refresh; a dead family is a real replay.
        assertThatThrownBy(() -> service.rotate("loser"))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeFamily(eq(familyId), eq(NOW), eq("REUSE_DETECTED"));
    }

    @Test
    void rotate_tokenRevokedByLogout_rejectsWithoutRevokingAgain() {
        RefreshSession loggedOut = RefreshSession.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .familyId(UUID.randomUUID())
                .tokenHash(RefreshSessionService.hash("logged-out"))
                .expiresAt(NOW.plusSeconds(600))
                .revokedAt(NOW.minusSeconds(1))
                .revokeReason("LOGOUT")
                .build();
        when(repository.findByTokenHashForUpdate(loggedOut.getTokenHash()))
                .thenReturn(Optional.of(loggedOut));

        assertThatThrownBy(() -> service.rotate("logged-out"))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void rotate_transientBlockLookupFailure_propagatesWithoutRevokingFamily() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshSession session = RefreshSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .familyId(familyId)
                .tokenHash(RefreshSessionService.hash("transient"))
                .expiresAt(NOW.plusSeconds(600))
                .build();
        when(repository.findByTokenHashForUpdate(session.getTokenHash())).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).email("user@test.local").role(Role.USER).build()));
        doThrow(new IllegalStateException("connection reset"))
                .when(accountSettingsService).assertNotBlocked(userId);

        // A database blip must not be mistaken for an administrative block: signing the user out of
        // every device is not an acceptable response to a transient failure.
        assertThatThrownBy(() -> service.rotate("transient"))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void purgeExpiredSessions_deletesRowsPastTheRetentionWindow() {
        when(repository.deleteExpiredBefore(any())).thenReturn(3);

        service.purgeExpiredSessions();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(NOW.minus(30, java.time.temporal.ChronoUnit.DAYS));
    }
}
