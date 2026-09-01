package com.vibegraph.auth.dto;

import java.util.List;
import java.util.UUID;

import com.vibegraph.auth.domain.entity.CreditLedger;

public record AdminCreditOverviewResponse(
        UUID userId,
        long currentCreditsLimit,
        long creditsUsed,
        long creditsAdjustment,
        long creditBalance,
        List<CreditLedger> ledgerHistory
) {}
