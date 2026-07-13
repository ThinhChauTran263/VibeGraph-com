package com.vibegraph.auth.service;

import java.time.YearMonth;
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
        String currentMonth = YearMonth.now().toString(); // e.g. "2023-10"
        
        return balanceRepository.findByUserIdAndPeriodMonth(userId, currentMonth)
                .orElseGet(() -> {
                    // Get current plan to snapshot allocated credits
                    UserAccountSettings settings = accountSettingsService.findSettings(userId);
                    long allocated = settings.getPlan() != null ? settings.getPlan().getMonthlyCredits() : 1000L;
                    
                    UserCreditBalance newBalance = UserCreditBalance.builder()
                            .userId(userId)
                            .periodMonth(currentMonth)
                            .planSnapshotCode(settings.getPlan() != null ? settings.getPlan().getCode() : "DEFAULT")
                            .allocatedCredits(allocated)
                            .usedCredits(0L)
                            .build();
                    log.info("Created new credit balance period {} for user {}", currentMonth, userId);
                    return balanceRepository.save(newBalance);
                });
    }

    @Transactional(readOnly = true)
    public void assertCreditsAvailable(UUID userId, long required) {
        if (required <= 0) return;
        
        UserCreditBalance balance = findOrCreateCurrentPeriod(userId);
        if (balance.getRemainingCredits() < required) {
            throw new InsufficientCreditsException("Insufficient credits to perform this operation. Required: " + required + ", Available: " + balance.getRemainingCredits());
        }
    }

    @Transactional
    public void deductCredits(UUID userId, long amount, String operationCode, String ref) {
        if (amount <= 0) return;

        UserCreditBalance balance = findOrCreateCurrentPeriod(userId);
        
        // Assert again inside transaction to prevent race conditions
        if (balance.getRemainingCredits() < amount) {
            throw new InsufficientCreditsException("Insufficient credits to perform this operation.");
        }

        // Update balance
        balance.setUsedCredits(balance.getUsedCredits() + amount);
        balanceRepository.save(balance);

        // Write ledger entry
        ledgerRepository.save(CreditLedger.builder()
                .userId(userId)
                .operationCode(operationCode)
                .amount(-amount)
                .referenceId(ref)
                .build());
                
        log.info("Deducted {} credits for user {}, operation {}", amount, userId, operationCode);
    }
}
