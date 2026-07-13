package com.vibegraph.auth.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.CreditLedger;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.domain.UserCreditBalance;
import com.vibegraph.auth.repository.CreditLedgerRepository;
import com.vibegraph.auth.repository.UserCreditBalanceRepository;
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

    @Transactional
    public UserCreditBalance findOrCreateCurrentPeriod(UUID userId) {
        LocalDate today = LocalDate.now();
        
        return balanceRepository.findActiveBalance(userId, today)
                .orElseGet(() -> {
                    // Get current plan to snapshot allocated credits
                    UserAccountSettings settings = accountSettingsService.findSettings(userId);
                    int allocated = settings.getPlan() != null ? settings.getPlan().getMonthlyCreditLimit() : 1000;
                    
                    LocalDate start = today.withDayOfMonth(1);
                    LocalDate end = today.withDayOfMonth(today.lengthOfMonth());

                    UserCreditBalance newBalance = UserCreditBalance.builder()
                            .userId(userId)
                            .periodStart(start)
                            .periodEnd(end)
                            .creditsLimitSnapshot(allocated)
                            .creditsUsed(0)
                            .creditsAdjustment(0)
                            .build();
                    log.info("Created new credit balance period for user {}", userId);
                    return balanceRepository.save(newBalance);
                });
    }

    private int getRemainingCredits(UserCreditBalance balance) {
        return (balance.getCreditsLimitSnapshot() + balance.getCreditsAdjustment()) - balance.getCreditsUsed();
    }

    @Transactional(readOnly = true)
    public void assertCreditsAvailable(UUID userId, long required) {
        if (required <= 0) return;
        
        UserCreditBalance balance = findOrCreateCurrentPeriod(userId);
        if (getRemainingCredits(balance) < required) {
            throw new InsufficientCreditsException("Insufficient credits to perform this operation. Required: " + required + ", Available: " + getRemainingCredits(balance));
        }
    }

    @Transactional
    public void deductCredits(UUID userId, long amount, String operationCode, String ref) {
        if (amount <= 0) return;

        UserCreditBalance balance = findOrCreateCurrentPeriod(userId);
        
        // Assert again inside transaction to prevent race conditions
        if (getRemainingCredits(balance) < amount) {
            throw new InsufficientCreditsException("Insufficient credits to perform this operation.");
        }

        // Update balance
        balance.setCreditsUsed(balance.getCreditsUsed() + (int)amount);
        balanceRepository.save(balance);

        // Write ledger entry
        ledgerRepository.save(CreditLedger.builder()
                .userId(userId)
                .balanceId(balance.getId())
                .operationCode(operationCode)
                .source("SYSTEM") // or pass it as param if needed
                .creditsDelta((int)-amount)
                .build());
                
        log.info("Deducted {} credits for user {}, operation {}", amount, userId, operationCode);
    }
}
