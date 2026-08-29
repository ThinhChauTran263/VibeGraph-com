package com.vibegraph.graph.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.ImportTierPricingService;
import com.vibegraph.common.exception.InsufficientCreditsException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImportCreditBilling")
class ImportCreditBillingTest {

    @Mock ImportTierPricingService tierPricingService;
    @Mock CreditBalanceService creditBalanceService;
    @InjectMocks ImportCreditBilling billing;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("resolves the size tier, validates the balance, then deducts with a WEB ledger entry")
    void chargeUpfront_happyPath() {
        when(tierPricingService.calculateCredits(ImportCreditBilling.OPERATION_IMPORT_ARCHIVE, 300))
                .thenReturn(5L);

        long charged = billing.chargeUpfront(userId, ImportCreditBilling.OPERATION_IMPORT_ARCHIVE, 300, "p1");

        assertThat(charged).isEqualTo(5L);
        InOrder order = Mockito.inOrder(tierPricingService, creditBalanceService);
        order.verify(tierPricingService).calculateCredits(ImportCreditBilling.OPERATION_IMPORT_ARCHIVE, 300);
        order.verify(creditBalanceService).assertCreditsAvailable(userId, 5L);
        order.verify(creditBalanceService).deductCredits(userId, 5L, "WEB", ImportCreditBilling.OPERATION_IMPORT_ARCHIVE, "p1");
    }

    @Test
    @DisplayName("an insufficient balance propagates 402-ready exception before any deduction")
    void chargeUpfront_insufficientBalance_skipsDeduction() {
        when(tierPricingService.calculateCredits(ImportCreditBilling.OPERATION_IMPORT_GITHUB, 1500))
                .thenReturn(15L);
        doThrow(new InsufficientCreditsException(
                "Insufficient credits to perform this operation. Required: 15, Available: 3", 15L, 3L))
                .when(creditBalanceService).assertCreditsAvailable(userId, 15L);

        assertThatThrownBy(() -> billing.chargeUpfront(userId, ImportCreditBilling.OPERATION_IMPORT_GITHUB, 1500, "p2"))
                .isInstanceOf(InsufficientCreditsException.class)
                .satisfies(e -> {
                    assertThat(((InsufficientCreditsException) e).getRequiredCredits()).isEqualTo(15L);
                    assertThat(((InsufficientCreditsException) e).getAvailableCredits()).isEqualTo(3L);
                });

        verify(creditBalanceService, never()).deductCredits(
                userId, 15L, "WEB", ImportCreditBilling.OPERATION_IMPORT_GITHUB, "p2");
    }

    @Test
    @DisplayName("an operation without configured tiers fails closed before touching the balance")
    void chargeUpfront_missingTiers_failsClosed() {
        when(tierPricingService.calculateCredits(ImportCreditBilling.OPERATION_CLI_PUSH, 10))
                .thenThrow(new IllegalStateException("No pricing tiers configured for operation: CLI_PUSH"));

        assertThatThrownBy(() -> billing.chargeUpfront(userId, ImportCreditBilling.OPERATION_CLI_PUSH, 10, "p3"))
                .isInstanceOf(IllegalStateException.class);

        verify(creditBalanceService, never()).assertCreditsAvailable(userId, 0L);
        verify(creditBalanceService, never())
                .deductCredits(Mockito.any(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @Test
    @DisplayName("the channel overload records the ledger entry under the given channel")
    void chargeUpfront_withChannel_deductsUnderThatChannel() {
        when(tierPricingService.calculateCredits(ImportCreditBilling.OPERATION_CLI_PUSH, 40))
                .thenReturn(2L);

        long charged = billing.chargeUpfront(userId, ImportCreditBilling.OPERATION_CLI_PUSH, 40, "p4", "CLI");

        assertThat(charged).isEqualTo(2L);
        verify(creditBalanceService).deductCredits(userId, 2L, "CLI", ImportCreditBilling.OPERATION_CLI_PUSH, "p4");
    }
}
