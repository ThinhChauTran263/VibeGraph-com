package com.vibegraph.auth.dto;

import java.math.BigDecimal;

import com.vibegraph.auth.domain.entity.CreditPricingRule;

public record AdminPricingRuleResponse(
        String operationCode,
        String displayName,
        BigDecimal baseCredits,
        BigDecimal perFileCredits,
        BigDecimal perMbCredits,
        BigDecimal per1kNodesCredits,
        int minimumCredits,
        boolean active) {

    public static AdminPricingRuleResponse from(CreditPricingRule rule) {
        return new AdminPricingRuleResponse(
                rule.getOperationCode(),
                rule.getDisplayName(),
                rule.getBaseCredits(),
                rule.getPerFileCredits(),
                rule.getPerMbCredits(),
                rule.getPer1kNodesCredits(),
                rule.getMinimumCredits(),
                rule.isActive());
    }
}
