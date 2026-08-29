package com.vibegraph.auth.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.CreditLedger;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.domain.UserCreditBalance;
import com.vibegraph.auth.repository.CreditLedgerRepository;
import com.vibegraph.auth.repository.UserCreditBalanceRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.InsufficientCreditsException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to manage user credit balances and ledgers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditBalanceService {

    private final UserCreditBalanceRepository balanceRepository;
    private final CreditLedgerRepository ledgerRepository;
    private final AccountSettingsService accountSettingsService;
    private final UserRepository userRepository;
    private final CreditPeriodCalculator periodCalculator;
    private final Clock clock;

    @Transactional
    public UserCreditBalance findOrCreateCurrentPeriod(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        User user = requireRegisteredUser(userId, false);
        CreditPeriodCalculator.CreditPeriod period = periodCalculator.currentPeriod(
                user.getCreatedAt(), today);
        return balanceRepository.findByUserIdAndPeriodStartAndPeriodEnd(
                        userId, period.periodStart(), period.periodEnd())
                .orElseGet(() -> initializeCurrentPeriod(userId, today));
    }

    @Transactional
    public void assertCreditsAvailable(UUID userId, long required) {
        if (required <= 0) {
            return;
        }

        UserCreditBalance balance = findOrCreateCurrentPeriod(userId);
        long remaining = getRemainingCredits(balance);
        if (remaining < required) {
            throw new InsufficientCreditsException(
                    "Insufficient credits to perform this operation. Required: " + required
                            + ", Available: " + remaining,
                    required, remaining);
        }
    }

    @Transactional
    public void deductCredits(
            UUID userId, long amount, String source, String operationCode, String projectId) {
        if (amount <= 0) {
            return;
        }

        int debit = Math.toIntExact(amount);
        UserCreditBalance balance = findOrCreateCurrentPeriod(userId);
        int updatedRows = balanceRepository.debitIfSufficient(balance.getId(), debit);
        if (updatedRows != 1) {
            throw new InsufficientCreditsException("Insufficient credits to perform this operation.");
        }

        ledgerRepository.save(CreditLedger.builder()
                .userId(userId)
                .projectId(projectId)
                .balanceId(balance.getId())
                .operationCode(operationCode)
                .source(source)
                .creditsDelta(Math.negateExact(debit))
                .build());

        log.info("Deducted {} credits for user {}, source {}, operation {}",
                amount, userId, source, operationCode);
    }

    @Transactional
    public void applyAdminAdjustment(UUID userId, int delta, String reason) {
        if (delta == 0) {
            throw new IllegalArgumentException("Credit adjustment must not be zero");
        }

        UserCreditBalance balance = findOrCreateCurrentPeriod(userId);
        int updatedRows = balanceRepository.adjustCredits(balance.getId(), delta);
        if (updatedRows != 1) {
            throw new IllegalArgumentException("Credit adjustment would exceed supported range");
        }

        ledgerRepository.save(CreditLedger.builder()
                .userId(userId)
                .balanceId(balance.getId())
                .source("ADMIN")
                .operationCode("ADMIN_ADJUSTMENT")
                .creditsDelta(delta)
                .metadata("{\"reason\":\"" + escapeJson(reason) + "\"}")
                .build());

        log.info("Applied admin credit adjustment {} for user {}", delta, userId);
    }

    @Transactional
    public void updateCurrentPeriodLimitSnapshot(UUID userId, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Credit limit must be non-negative");
        }

        UserCreditBalance balance = findOrCreateCurrentPeriod(userId);
        int updatedRows = balanceRepository.updateCreditsLimitSnapshot(balance.getId(), limit);
        if (updatedRows != 1) {
            throw new IllegalArgumentException("Credit limit update failed");
        }
    }

    private UserCreditBalance initializeCurrentPeriod(UUID userId, LocalDate today) {
        User lockedUser = requireRegisteredUser(userId, true);
        CreditPeriodCalculator.CreditPeriod period = periodCalculator.currentPeriod(
                lockedUser.getCreatedAt(), today);
        return balanceRepository.findByUserIdAndPeriodStartAndPeriodEnd(
                        userId, period.periodStart(), period.periodEnd())
                .orElseGet(() -> transitionLegacyOrCreate(userId, today, period));
    }

    private UserCreditBalance transitionLegacyOrCreate(
            UUID userId,
            LocalDate today,
            CreditPeriodCalculator.CreditPeriod period) {
        List<UserCreditBalance> activeBalances =
                balanceRepository.findActiveBalancesForUpdate(userId, today);
        if (activeBalances.size() > 1) {
            throw new IllegalStateException("Multiple active credit balances detected");
        }
        if (activeBalances.size() == 1) {
            return reanchorBalance(activeBalances.get(0), period);
        }
        return createBalance(userId, period);
    }

    private User requireRegisteredUser(UUID userId, boolean forUpdate) {
        User user = (forUpdate
                        ? userRepository.findByIdForUpdate(userId)
                        : userRepository.findById(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getCreatedAt() == null) {
            throw new IllegalStateException("User registration timestamp is not available");
        }
        return user;
    }

    private UserCreditBalance reanchorBalance(
            UserCreditBalance balance, CreditPeriodCalculator.CreditPeriod period) {
        balance.setPeriodStart(period.periodStart());
        balance.setPeriodEnd(period.periodEnd());
        log.info("Re-anchored credit balance period for user {}", balance.getUserId());
        return balanceRepository.save(balance);
    }

    private UserCreditBalance createBalance(
            UUID userId, CreditPeriodCalculator.CreditPeriod period) {
        UserAccountSettings settings = accountSettingsService.findSettings(userId);
        int allocated = settings.getCreditQuotaOverride() != null
                ? settings.getCreditQuotaOverride()
                : settings.getPlan().getMonthlyCreditLimit();

        UserCreditBalance newBalance = UserCreditBalance.builder()
                .userId(userId)
                .periodStart(period.periodStart())
                .periodEnd(period.periodEnd())
                .creditsLimitSnapshot(allocated)
                .creditsUsed(0)
                .creditsAdjustment(0)
                .build();
        log.info("Created new credit balance period for user {}", userId);
        return balanceRepository.save(newBalance);
    }

    private long getRemainingCredits(UserCreditBalance balance) {
        return (long) balance.getCreditsLimitSnapshot()
                + balance.getCreditsAdjustment()
                - balance.getCreditsUsed();
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
