package com.vibegraph.graph.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.ImportTierPricingService;
import com.vibegraph.common.exception.InsufficientCreditsException;

import lombok.RequiredArgsConstructor;

/**
 * Charges credits for import/analyze operations whose size — the number of
 * {@code .java} files involved — is known <b>before</b> the heavy analysis
 * runs. Charging upfront means an exhausted balance fails fast with
 * {@link InsufficientCreditsException} (HTTP 402) instead of letting the
 * server burn analysis resources on work it can never bill.
 *
 * <p>The cost is a fixed amount per project-size tier (small / medium /
 * large / xlarge). Thresholds and credits live in the
 * {@code import_pricing_tiers} table, configured per import method through
 * {@code /api/admin/import-pricing}; nothing about the numbers is hardcoded
 * beyond the operation codes.
 */
@Service
@RequiredArgsConstructor
public class ImportCreditBilling {

    public static final String OPERATION_IMPORT_ARCHIVE = "IMPORT_ARCHIVE";
    public static final String OPERATION_IMPORT_GITHUB = "IMPORT_GITHUB";
    public static final String OPERATION_CLI_PUSH = "CLI_PUSH";

    private final ImportTierPricingService tierPricingService;
    private final CreditBalanceService creditBalanceService;

    /**
     * Calculates, validates, and deducts the tier charge for one operation.
     *
     * @param userId        account to bill
     * @param operationCode tier set key (e.g. {@link #OPERATION_IMPORT_ARCHIVE})
     * @param fileCount     number of {@code .java} files the operation covers;
     *                      selects the size tier
     * @param projectId     billed project, recorded in the credit ledger
     * @return the deducted amount (0 when the matched tier is priced free)
     * @throws InsufficientCreditsException when the period balance cannot cover the charge
     */
    public long chargeUpfront(UUID userId, String operationCode, int fileCount, String projectId) {
        return chargeUpfront(userId, operationCode, fileCount, projectId, "WEB");
    }

    /**
     * Same as {@link #chargeUpfront(UUID, String, int, String)} but records the
     * given ledger channel (e.g. {@code "CLI"} for CLI pushes).
     */
    public long chargeUpfront(UUID userId, String operationCode, int fileCount, String projectId,
            String channel) {
        long required = tierPricingService.calculateCredits(operationCode, fileCount);
        creditBalanceService.assertCreditsAvailable(userId, required);
        creditBalanceService.deductCredits(userId, required, channel, operationCode, projectId);
        return required;
    }
}
