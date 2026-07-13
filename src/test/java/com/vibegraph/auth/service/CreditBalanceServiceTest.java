package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.CreditLedger;
import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.domain.UserCreditBalance;
import com.vibegraph.common.exception.InsufficientCreditsException;
import com.vibegraph.auth.repository.CreditLedgerRepository;
import com.vibegraph.auth.repository.UserCreditBalanceRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditBalanceService")
class CreditBalanceServiceTest {

    @Mock
    private UserCreditBalanceRepository balanceRepository;
    
    @Mock
    private CreditLedgerRepository ledgerRepository;
    
    @Mock
    private AccountSettingsService accountSettingsService;

    private CreditBalanceService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CreditBalanceService(balanceRepository, ledgerRepository, accountSettingsService);
    }

    @Test
    @DisplayName("should deduct credits successfully when enough credits are available")
    void shouldDeductCreditsSuccessfully() {
        UserCreditBalance balance = new UserCreditBalance();
        balance.setUserId(userId);
        balance.setCreditsLimitSnapshot(5000);
        balance.setCreditsUsed(0);
        UserAccountSettings settings = new UserAccountSettings();
        settings.setPlan(Plan.builder().monthlyCreditLimit(5000).build());
        
        when(balanceRepository.findActiveBalance(eq(userId), any(java.time.LocalDate.class))).thenReturn(Optional.of(balance));

        service.deductCredits(userId, 200L, "IMPORT_GITHUB", "p1");

        assertThat(balance.getCreditsUsed()).isEqualTo(200);
        verify(balanceRepository).save(balance);
        verify(ledgerRepository).save(any(CreditLedger.class));
    }

    @Test
    @DisplayName("should throw InsufficientCreditsException when not enough credits")
    void shouldThrowWhenNotEnoughCredits() {
        UserCreditBalance balance = new UserCreditBalance();
        balance.setUserId(userId);
        balance.setCreditsLimitSnapshot(100);
        balance.setCreditsUsed(0);
        UserAccountSettings settings = new UserAccountSettings();
        settings.setPlan(Plan.builder().monthlyCreditLimit(100).build());
        
        when(balanceRepository.findActiveBalance(eq(userId), any(java.time.LocalDate.class))).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.deductCredits(userId, 200L, "IMPORT_GITHUB", "p1"))
                .isInstanceOf(InsufficientCreditsException.class)
                .hasMessageContaining("Insufficient credits");

        verify(balanceRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("assertCreditsAvailable should throw when insufficient")
    void assertCreditsAvailableShouldThrow() {
        UserCreditBalance balance = new UserCreditBalance();
        balance.setUserId(userId);
        balance.setCreditsLimitSnapshot(50);
        balance.setCreditsUsed(0);
        UserAccountSettings settings = new UserAccountSettings();
        settings.setPlan(Plan.builder().monthlyCreditLimit(50).build());
        
        when(balanceRepository.findActiveBalance(eq(userId), any(java.time.LocalDate.class))).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.assertCreditsAvailable(userId, 100L))
                .isInstanceOf(InsufficientCreditsException.class);
    }
}
