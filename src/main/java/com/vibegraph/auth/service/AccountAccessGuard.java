package com.vibegraph.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.repository.projection.AuthSnapshot;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.AccountDeactivatedException;
import com.vibegraph.common.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

/** Revalidates account access at long-lived backend boundaries such as WebSocket delivery. */
@Service
@RequiredArgsConstructor
public class AccountAccessGuard {

    private static final String DEFAULT_DEACTIVATED_REASON = "Account closed by administrator";

    private final UserRepository userRepository;
    private final AccountSettingsService accountSettingsService;

    @Transactional(readOnly = true)
    public void assertProductAccess(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        accountSettingsService.assertNotBlocked(userId);
        if (user.isDeactivated()) {
            throw new AccountDeactivatedException(
                    "Account is deactivated",
                    safeDeactivationReason(user.getDeactivationReasonSafe()));
        }
    }

    /**
     * Resolve everything the per-request authentication filter needs in a single query.
     *
     * <p>{@link #assertProductAccess(UUID)} loads the user and its settings separately, and the
     * filter then loaded the user a second time and checked the refresh session in a third round
     * trip. With {@code open-in-view: false} none of those shared a persistence context, so an
     * authenticated request cost four reads. This method answers all of it at once and returns the
     * verdict instead of throwing, so the caller can decide which restrictions a given route
     * tolerates.
     *
     * @param sessionId the JWT {@code sid}, or {@code null} for a token issued before sessions
     *                  existed — such a token is still accepted, matching the previous behaviour
     * @throws UnauthorizedException when the account no longer exists
     */
    @Transactional(readOnly = true)
    public AccountAccessDecision authenticate(UUID userId, UUID sessionId) {
        AuthSnapshot snapshot = userRepository.findAuthSnapshot(userId, sessionId, Instant.now())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        return new AccountAccessDecision(
                new AuthenticatedUser(snapshot.id(), snapshot.email(), snapshot.role(), sessionId),
                restrictionOf(snapshot),
                sessionId == null || snapshot.sessionActive());
    }

    /** Blocked outranks deactivated, matching the order inside {@link #assertProductAccess(UUID)}. */
    private AccountBlockedException restrictionOf(AuthSnapshot snapshot) {
        if (snapshot.blocked()) {
            return new AccountBlockedException(
                    "Account is blocked", safeBlockedReason(snapshot.blockedReasonSafe()));
        }
        if (snapshot.deactivated()) {
            return new AccountDeactivatedException(
                    "Account is deactivated", safeDeactivationReason(snapshot.deactivationReasonSafe()));
        }
        return null;
    }

    private String safeBlockedReason(String reason) {
        return reason == null || reason.isBlank()
                ? AccountSettingsService.DEFAULT_BLOCKED_REASON
                : reason;
    }

    private String safeDeactivationReason(String reason) {
        return reason == null || reason.isBlank() ? DEFAULT_DEACTIVATED_REASON : reason;
    }

    /**
     * Outcome of one authentication read.
     *
     * @param restriction   the restriction that applies, or {@code null} when the account is fine;
     *                      returned rather than thrown because some routes stay open to blocked and
     *                      deactivated accounts (logout, session state, support reports)
     * @param sessionUsable whether the refresh session behind the access token is still live
     */
    public record AccountAccessDecision(
            AuthenticatedUser principal,
            AccountBlockedException restriction,
            boolean sessionUsable) {
    }

    @Transactional(readOnly = true)
    public boolean canAccessRealtime(UUID userId) {
        return canAccessSupportRealtime(userId)
                && !accountSettingsService.isBlocked(userId);
    }

    @Transactional(readOnly = true)
    public boolean canAccessSupportRealtime(UUID userId) {
        return userId != null
                && userRepository.findById(userId)
                        .filter(user -> !user.isDeactivated())
                        .isPresent();
    }
}
