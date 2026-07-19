package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.CreditLedger;
import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.domain.UserCreditBalance;
import com.vibegraph.auth.repository.CreditLedgerRepository;
import com.vibegraph.auth.repository.UserCreditBalanceRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.InsufficientCreditsException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditBalanceService")
class CreditBalanceServiceTest {

    private static final Instant REGISTERED_AT = Instant.parse("2024-01-31T08:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2024-03-30T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2024, 3, 30);

    @Mock UserCreditBalanceRepository balanceRepository;
    @Mock CreditLedgerRepository ledgerRepository;
    @Mock AccountSettingsService accountSettingsService;
    @Mock UserRepository userRepository;

    private CreditPeriodCalculator periodCalculator;
    private CreditBalanceService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        periodCalculator = new CreditPeriodCalculator();
        service = new CreditBalanceService(
                balanceRepository,
                ledgerRepository,
                accountSettingsService,
                userRepository,
                periodCalculator,
                FIXED_CLOCK);
    }

    @Test
    @DisplayName("creates the registration-day period using the account credit override")
    void findOrCreateCurrentPeriod_createsCanonicalPeriodWithOverride() {
        CreditPeriodCalculator.CreditPeriod expected = expectedPeriod();
        UserAccountSettings settings = settings(5000);
        settings.setCreditQuotaOverride(6200);
        stubMissingExactPeriod(expected);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user()));
        when(balanceRepository.findByUserIdAndPeriodStartAndPeriodEnd(
                userId, expected.periodStart(), expected.periodEnd())).thenReturn(Optional.empty());
        when(balanceRepository.findActiveBalancesForUpdate(userId, TODAY)).thenReturn(List.of());
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        when(balanceRepository.save(any(UserCreditBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCreditBalance result = service.findOrCreateCurrentPeriod(userId);

        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(result.getPeriodEnd()).isEqualTo(LocalDate.of(2024, 3, 30));
        assertThat(result.getCreditsLimitSnapshot()).isEqualTo(6200);
        assertThat(result.getCreditsUsed()).isZero();
    }

    @Test
    @DisplayName("returns an exact registration-day balance without legacy migration")
    void findOrCreateCurrentPeriod_exactBalanceWins() {
        UserCreditBalance exact = activeBalance(5000, 12);
        stubExactPeriod(exact);

        UserCreditBalance result = service.findOrCreateCurrentPeriod(userId);

        assertThat(result).isSameAs(exact);
        verify(userRepository, never()).findByIdForUpdate(userId);
        verify(balanceRepository, never()).findActiveBalancesForUpdate(any(), any());
        verify(balanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("re-anchors one legacy balance without resetting usage or changing its identity")
    void findOrCreateCurrentPeriod_reanchorsLegacyBalancePreservingIdentityAndUsage() {
        CreditPeriodCalculator.CreditPeriod expected = expectedPeriod();
        UUID balanceId = UUID.randomUUID();
        UserCreditBalance legacy = UserCreditBalance.builder()
                .id(balanceId)
                .userId(userId)
                .periodStart(LocalDate.of(2024, 3, 1))
                .periodEnd(LocalDate.of(2024, 3, 31))
                .creditsLimitSnapshot(5000)
                .creditsUsed(321)
                .creditsAdjustment(45)
                .build();
        stubMissingExactPeriod(expected);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user()));
        when(balanceRepository.findByUserIdAndPeriodStartAndPeriodEnd(
                userId, expected.periodStart(), expected.periodEnd())).thenReturn(Optional.empty());
        when(balanceRepository.findActiveBalancesForUpdate(userId, TODAY))
                .thenReturn(List.of(legacy));
        when(balanceRepository.save(legacy)).thenReturn(legacy);

        UserCreditBalance result = service.findOrCreateCurrentPeriod(userId);

        assertThat(result.getId()).isEqualTo(balanceId);
        assertThat(result.getPeriodStart()).isEqualTo(expected.periodStart());
        assertThat(result.getPeriodEnd()).isEqualTo(expected.periodEnd());
        assertThat(result.getCreditsUsed()).isEqualTo(321);
        assertThat(result.getCreditsAdjustment()).isEqualTo(45);
        assertThat(result.getCreditsLimitSnapshot()).isEqualTo(5000);
        verify(balanceRepository).save(legacy);
        verify(accountSettingsService, never()).findSettings(userId);
    }

    @Test
    @DisplayName("multiple active legacy balances fail closed without changing or creating a balance")
    void findOrCreateCurrentPeriod_multipleActiveLegacyBalances_failClosed() {
        CreditPeriodCalculator.CreditPeriod expected = expectedPeriod();
        UserCreditBalance first = legacyBalance(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31));
        UserCreditBalance second = legacyBalance(LocalDate.of(2024, 3, 15), LocalDate.of(2024, 4, 14));
        stubMissingExactPeriod(expected);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user()));
        when(balanceRepository.findByUserIdAndPeriodStartAndPeriodEnd(
                userId, expected.periodStart(), expected.periodEnd())).thenReturn(Optional.empty());
        when(balanceRepository.findActiveBalancesForUpdate(userId, TODAY))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.findOrCreateCurrentPeriod(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple active credit balances");

        verify(balanceRepository, never()).save(any());
        verify(accountSettingsService, never()).findSettings(userId);
    }

    @Test
    @DisplayName("deducts with one atomic conditional update and writes an attributed ledger")
    void deductCredits_writesAttributedLedgerAfterConditionalUpdate() {
        UserCreditBalance balance = activeBalance(5000, 0);
        balance.setId(UUID.randomUUID());
        stubExactPeriod(balance);
        when(balanceRepository.debitIfSufficient(balance.getId(), 200)).thenReturn(1);

        service.deductCredits(userId, 200L, "CLI", "CLI_PUSH", "p1");

        verify(balanceRepository).debitIfSufficient(balance.getId(), 200);
        verify(balanceRepository, never()).save(balance);
        assertThat(balance.getCreditsUsed()).isZero();
        ArgumentCaptor<CreditLedger> ledgerCaptor = ArgumentCaptor.forClass(CreditLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getBalanceId()).isEqualTo(balance.getId());
        assertThat(ledgerCaptor.getValue().getSource()).isEqualTo("CLI");
        assertThat(ledgerCaptor.getValue().getOperationCode()).isEqualTo("CLI_PUSH");
        assertThat(ledgerCaptor.getValue().getProjectId()).isEqualTo("p1");
        assertThat(ledgerCaptor.getValue().getCreditsDelta()).isEqualTo(-200);
    }

    @Test
    @DisplayName("conditional debit miss throws without balance save or ledger")
    void deductCredits_conditionalMiss_throwsWithoutWrites() {
        UserCreditBalance balance = activeBalance(100, 0);
        balance.setId(UUID.randomUUID());
        stubExactPeriod(balance);
        when(balanceRepository.debitIfSufficient(balance.getId(), 200)).thenReturn(0);

        assertThatThrownBy(() -> service.deductCredits(userId, 200L, "MCP", "MCP_TOOL_CALL", null))
                .isInstanceOf(InsufficientCreditsException.class)
                .hasMessageContaining("Insufficient credits");

        verify(balanceRepository).debitIfSufficient(balance.getId(), 200);
        verify(balanceRepository, never()).save(balance);
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("non-positive deductions are no-ops")
    void deductCredits_nonPositive_doesNothing() {
        service.deductCredits(userId, 0, "CLI", "CLI_PUSH", "p1");
        service.deductCredits(userId, -1, "CLI", "CLI_PUSH", "p1");

        verifyNoInteractions(
                balanceRepository,
                ledgerRepository,
                accountSettingsService,
                userRepository);
    }

    @Test
    @DisplayName("admin adjustment uses one atomic update and writes ledger after success")
    void applyAdminAdjustment_atomicUpdateThenLedger() {
        UserCreditBalance balance = activeBalance(5000, 0);
        balance.setId(UUID.randomUUID());
        stubExactPeriod(balance);
        when(balanceRepository.adjustCredits(balance.getId(), 75)).thenReturn(1);

        service.applyAdminAdjustment(userId, 75, "bonus \"credits\"\nnext\tline");

        verify(balanceRepository).adjustCredits(balance.getId(), 75);
        verify(balanceRepository, never()).save(balance);
        ArgumentCaptor<CreditLedger> ledgerCaptor = ArgumentCaptor.forClass(CreditLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getBalanceId()).isEqualTo(balance.getId());
        assertThat(ledgerCaptor.getValue().getSource()).isEqualTo("ADMIN");
        assertThat(ledgerCaptor.getValue().getOperationCode()).isEqualTo("ADMIN_ADJUSTMENT");
        assertThat(ledgerCaptor.getValue().getCreditsDelta()).isEqualTo(75);
        assertThat(ledgerCaptor.getValue().getMetadata()).contains("\\\"credits\\\"");
        assertThat(ledgerCaptor.getValue().getMetadata()).contains("\\nnext\\tline");
    }

    @Test
    @DisplayName("admin adjustment conditional miss does not write ledger")
    void applyAdminAdjustment_updateMiss_throwsWithoutLedger() {
        UserCreditBalance balance = activeBalance(5000, 0);
        balance.setId(UUID.randomUUID());
        stubExactPeriod(balance);
        when(balanceRepository.adjustCredits(balance.getId(), Integer.MAX_VALUE)).thenReturn(0);

        assertThatThrownBy(() -> service.applyAdminAdjustment(userId, Integer.MAX_VALUE, "too much"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported range");

        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("credit limit snapshot update uses one atomic update without saving stale balance")
    void updateCurrentPeriodLimitSnapshot_atomicUpdate() {
        UserCreditBalance balance = activeBalance(5000, 0);
        balance.setId(UUID.randomUUID());
        stubExactPeriod(balance);
        when(balanceRepository.updateCreditsLimitSnapshot(balance.getId(), 9000)).thenReturn(1);

        service.updateCurrentPeriodLimitSnapshot(userId, 9000);

        verify(balanceRepository).updateCreditsLimitSnapshot(balance.getId(), 9000);
        verify(balanceRepository, never()).save(balance);
    }

    @Test
    @DisplayName("deduction amount outside the database integer range fails safely")
    void deductCredits_unrepresentableAmount_failsSafely() {
        assertThatThrownBy(() -> service.deductCredits(
                        userId, (long) Integer.MAX_VALUE + 1, "CLI", "CLI_PUSH", "p1"))
                .isInstanceOf(ArithmeticException.class);

        verify(balanceRepository, never()).debitIfSufficient(any(), anyInt());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("assertCreditsAvailable rejects a charge above the current remaining balance")
    void assertCreditsAvailable_insufficient_throws() {
        UserCreditBalance balance = activeBalance(50, 0);
        stubExactPeriod(balance);

        assertThatThrownBy(() -> service.assertCreditsAvailable(userId, 100L))
                .isInstanceOf(InsufficientCreditsException.class);
    }

    @Test
    @DisplayName("credit exhaustion exposes the stable API error code")
    void insufficientCredits_usesCreditExhaustedCode() {
        assertThat(new InsufficientCreditsException("exhausted").getCode())
                .isEqualTo("CREDIT_EXHAUSTED");
    }

    private void stubMissingExactPeriod(CreditPeriodCalculator.CreditPeriod expected) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(balanceRepository.findByUserIdAndPeriodStartAndPeriodEnd(
                userId, expected.periodStart(), expected.periodEnd())).thenReturn(Optional.empty());
    }

    private void stubExactPeriod(UserCreditBalance balance) {
        CreditPeriodCalculator.CreditPeriod period = expectedPeriod();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(balanceRepository.findByUserIdAndPeriodStartAndPeriodEnd(
                userId, period.periodStart(), period.periodEnd())).thenReturn(Optional.of(balance));
    }

    private CreditPeriodCalculator.CreditPeriod expectedPeriod() {
        return periodCalculator.currentPeriod(REGISTERED_AT, TODAY);
    }

    private User user() {
        return User.builder().id(userId).createdAt(REGISTERED_AT).build();
    }

    private UserAccountSettings settings(int monthlyLimit) {
        return UserAccountSettings.builder()
                .userId(userId)
                .plan(Plan.builder().monthlyCreditLimit(monthlyLimit).build())
                .build();
    }

    private UserCreditBalance activeBalance(int limit, int used) {
        CreditPeriodCalculator.CreditPeriod period = expectedPeriod();
        return UserCreditBalance.builder()
                .userId(userId)
                .periodStart(period.periodStart())
                .periodEnd(period.periodEnd())
                .creditsLimitSnapshot(limit)
                .creditsUsed(used)
                .creditsAdjustment(0)
                .build();
    }

    private UserCreditBalance legacyBalance(LocalDate start, LocalDate end) {
        return UserCreditBalance.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .periodStart(start)
                .periodEnd(end)
                .creditsLimitSnapshot(5000)
                .creditsUsed(100)
                .creditsAdjustment(0)
                .build();
    }
}
