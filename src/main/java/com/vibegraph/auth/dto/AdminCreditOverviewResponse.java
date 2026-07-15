package com.vibegraph.auth.dto;

import java.util.List;
import java.util.UUID;

import com.vibegraph.auth.domain.CreditLedger;

public record AdminCreditOverviewResponse(
        UUID userId,
        int currentCreditsLimit,
        int creditsUsed,
        int creditsAdjustment,
        int creditBalance,
        List<CreditLedger> ledgerHistory
) {}
