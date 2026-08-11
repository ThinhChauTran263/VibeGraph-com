package com.vibegraph.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.config.JwtProperties;
import com.vibegraph.auth.domain.RefreshSession;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.RefreshSessionRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.UnauthorizedException;

/** Issues opaque refresh tokens, rotates them once, and detects replay. */
@Service
public class RefreshSessionService {

    private static final String ROTATED = "ROTATED";
    private static final String REUSE_DETECTED = "REUSE_DETECTED";
    private static final String SECURITY_EVENT = "SECURITY_EVENT";
    private static final int TOKEN_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(RefreshSessionService.class);

    private final RefreshSessionRepository repository;
    private final UserRepository userRepository;
    private final AccountSettingsService accountSettingsService;
    private final long refreshExpirationMs;
    private final long refreshGraceMs;
    private final int refreshRetentionDays;
    private final Clock clock;
    private final SecureRandom secureRandom;

    /** Constructor used by Spring; production time is always UTC. */
    @Autowired
    public RefreshSessionService(
            RefreshSessionRepository repository,
            UserRepository userRepository,
            AccountSettingsService accountSettingsService,
            JwtProperties properties) {
        this(repository, userRepository, accountSettingsService, properties,
                Clock.systemUTC(), new SecureRandom());
    }

    RefreshSessionService(
            RefreshSessionRepository repository,
            UserRepository userRepository,
            AccountSettingsService accountSettingsService,
            JwtProperties properties,
            Clock clock,
            SecureRandom secureRandom) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.accountSettingsService = accountSettingsService;
        this.refreshExpirationMs = positiveDuration(properties.getRefreshExpirationMs(), "refresh");
        this.refreshGraceMs = nonNegative(properties.getRefreshGraceMs(), "refresh grace");
        this.refreshRetentionDays = (int) nonNegative(properties.getRefreshRetentionDays(), "refresh retention");
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /** A raw token is returned once and must only be sent in an HttpOnly cookie. */
    @Transactional
    public SessionToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusMillis(refreshExpirationMs);
        String rawToken = generateToken();
        RefreshSession session = RefreshSession.builder()
                .userId(user.getId())
                .familyId(UUID.randomUUID())
                .tokenHash(hash(rawToken))
                .expiresAt(expiresAt)
                .build();
        RefreshSession saved = repository.save(session);
        return new SessionToken(saved.getId(), rawToken, expiresAt);
    }

    /** Rotate an active token; security revocations must survive the rejected request. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RotatedSession rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        Instant now = clock.instant();
        RefreshSession current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(this::invalidToken);
        if (current.getRevokedAt() != null) {
            return rotateAlreadyRevoked(current, now);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            repository.revokeFamily(current.getFamilyId(), now, "EXPIRED");
            throw invalidToken();
        }

        User user = requireUsableAccount(current, now);
        SessionToken replacement = issueSibling(current);
        current.setLastUsedAt(now);
        current.setRevokedAt(now);
        current.setRevokeReason(ROTATED);
        current.setReplacedById(replacement.sessionId());
        repository.save(current);
        return new RotatedSession(user, replacement);
    }

    /**
     * Handle a token that was already revoked: either a concurrent refresh or a genuine replay.
     *
     * <p>Two tabs polling on the same timer refresh within milliseconds of each other, so the
     * loser inevitably presents a token the winner just rotated. Treating that as theft would sign
     * the user out of every device. Inside the grace window — and only while the replacement issued
     * by the winner is still live — the loser gets its own sibling token instead. Everything else
     * is still a replay and still burns the whole family.
     */
    private RotatedSession rotateAlreadyRevoked(RefreshSession current, Instant now) {
        if (!ROTATED.equals(current.getRevokeReason())) {
            // Already revoked by logout, expiry or a previous replay: nothing left to protect.
            throw invalidToken();
        }
        if (!isWithinGrace(current, now) || !hasLiveReplacement(current, now)) {
            log.warn("Refresh token replay detected; revoking session family. userId={}, familyId={}",
                    current.getUserId(), current.getFamilyId());
            repository.revokeFamily(current.getFamilyId(), now, REUSE_DETECTED);
            throw invalidToken();
        }
        User user = requireUsableAccount(current, now);
        // Deliberately does NOT revoke the winner's replacement: both tabs stay signed in, and from
        // here on they hold distinct tokens so they cannot collide again.
        return new RotatedSession(user, issueSibling(current));
    }

    private boolean isWithinGrace(RefreshSession current, Instant now) {
        Instant revokedAt = current.getRevokedAt();
        return revokedAt != null && !revokedAt.plusMillis(refreshGraceMs).isBefore(now);
    }

    private boolean hasLiveReplacement(RefreshSession current, Instant now) {
        if (current.getReplacedById() == null) {
            return false;
        }
        return repository.findById(current.getReplacedById())
                .filter(replacement -> replacement.getRevokedAt() == null)
                .filter(replacement -> replacement.getExpiresAt().isAfter(now))
                .isPresent();
    }

    /**
     * Mint another token in the same family, keeping the family's absolute expiry.
     *
     * <p>Returns the raw token with the saved row because the raw value exists only here — the
     * table stores its SHA-256 and nothing else.
     */
    private SessionToken issueSibling(RefreshSession current) {
        String rawToken = generateToken();
        RefreshSession sibling = RefreshSession.builder()
                .userId(current.getUserId())
                .familyId(current.getFamilyId())
                .tokenHash(hash(rawToken))
                .expiresAt(current.getExpiresAt())
                .build();
        RefreshSession saved = repository.save(sibling);
        return new SessionToken(saved.getId(), rawToken, saved.getExpiresAt());
    }

    private User requireUsableAccount(RefreshSession current, Instant now) {
        User user = userRepository.findById(current.getUserId()).orElseThrow(this::invalidToken);
        if (!isAccountUsable(user)) {
            repository.revokeFamily(current.getFamilyId(), now, SECURITY_EVENT);
            throw invalidToken();
        }
        return user;
    }

    /**
     * Delete refresh sessions whose absolute expiry passed longer ago than the retention window.
     *
     * <p>Rotation only inserts rows, so an active user adds roughly one per access-token lifetime.
     * Without this sweep the table grows for the life of the deployment.
     */
    @Scheduled(cron = "${vibegraph.auth.jwt.refresh-sweep-cron:0 15 3 * * ?}")
    @Transactional
    public void purgeExpiredSessions() {
        Instant cutoff = clock.instant().minus(refreshRetentionDays, ChronoUnit.DAYS);
        int removed = repository.deleteExpiredBefore(cutoff);
        if (removed > 0) {
            log.info("Refresh session sweep deleted {} sessions expired before {}", removed, cutoff);
        }
    }

    /** Revoke the family represented by a browser logout token; unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHashForUpdate(hash(rawToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                repository.revokeFamily(session.getFamilyId(), clock.instant(), safeReason(reason));
            }
        });
    }

    /** Revoke every active session for an account after a security-sensitive mutation. */
    @Transactional
    public void revokeAllForUser(UUID userId, String reason) {
        if (userId != null) {
            repository.revokeAllForUser(userId, clock.instant(), safeReason(reason));
        }
    }

    /** Check whether an access JWT is still backed by an active refresh session. */
    @Transactional(readOnly = true)
    public boolean isAccessSessionActive(UUID sessionId, UUID userId) {
        return sessionId == null || repository.isActive(sessionId, userId, clock.instant());
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Whether the account behind a refresh session may still be signed in.
     *
     * <p>Only {@link AccountBlockedException} counts as "not usable". A broader catch would read a
     * transient failure inside the settings lookup as an administrative block and permanently
     * revoke every session the user has — a database blip would sign them out for good. Unexpected
     * failures propagate instead, so the request fails without destroying the session family.
     */
    private boolean isAccountUsable(User user) {
        if (user.isDeactivated()) {
            return false;
        }
        try {
            accountSettingsService.assertNotBlocked(user.getId());
            return true;
        } catch (AccountBlockedException ex) {
            return false;
        }
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return SECURITY_EVENT;
        }
        return reason.length() > 40 ? reason.substring(0, 40) : reason;
    }

    private UnauthorizedException invalidToken() {
        return new UnauthorizedException("Invalid refresh token");
    }

    private long positiveDuration(long value, String label) {
        if (value <= 0) {
            throw new IllegalStateException("JWT " + label + " expiration must be positive");
        }
        return value;
    }

    /** Zero is allowed here: it disables the grace window, or sweeps as soon as a row expires. */
    private long nonNegative(long value, String label) {
        if (value < 0) {
            throw new IllegalStateException("JWT " + label + " must not be negative");
        }
        return value;
    }

    /** Safe metadata returned alongside a one-time raw refresh token. */
    public record SessionToken(UUID sessionId, String rawToken, Instant expiresAt) {
    }

    /** Result of a successful rotation, including the account to mint a new access JWT for. */
    public record RotatedSession(User user, SessionToken token) {
    }
}
