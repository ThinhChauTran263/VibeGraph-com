package com.vibegraph.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.vibegraph.auth.domain.CreditLedger;

public record AccountCreditLedgerResponse(
        UUID id,
        String source,
        String operationCode,
        int creditsDelta,
        String projectId,
        Instant createdAt
) {
    public static AccountCreditLedgerResponse from(CreditLedger ledger) {
        return new AccountCreditLedgerResponse(
                ledger.getId(),
                ledger.getSource(),
                ledger.getOperationCode(),
                ledger.getCreditsDelta(),
                ledger.getProjectId(),
                ledger.getCreatedAt());
    }
}
