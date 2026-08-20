package com.vibegraph.auth.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.CreditPricingRule;
import com.vibegraph.auth.repository.CreditPricingRuleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service to calculate required credits for a specific operation.
 * The charge is {@code base + files * perFile + sourceMb * perMb} with a
 * single final ceiling, then floored at the rule's {@code minimumCredits}
 * so small operations never fall below the configured floor.
 */
@Service
@RequiredArgsConstructor
public class CreditPricingService {

    private static final long BYTES_PER_MIB = 1_048_576L;
    private static final BigDecimal BYTES_PER_MIB_DECIMAL = BigDecimal.valueOf(BYTES_PER_MIB);

    private final CreditPricingRuleRepository pricingRuleRepository;

    @Transactional(readOnly = true)
    public long calculateCredits(String operationCode, int fileCount, long sourceBytes) {
        if (fileCount < 0) {
            throw new IllegalArgumentException("fileCount must be non-negative");
        }
        if (sourceBytes < 0) {
            throw new IllegalArgumentException("sourceBytes must be non-negative");
        }

        CreditPricingRule rule = pricingRuleRepository.findByOperationCode(operationCode)
                .filter(CreditPricingRule::isActive)
                .orElseThrow(() -> new IllegalStateException(
                        "No active pricing rule found for operation: " + operationCode));

        BigDecimal sourceMb = BigDecimal.valueOf(sourceBytes).divide(BYTES_PER_MIB_DECIMAL);
        BigDecimal total = rule.getBaseCredits()
                .add(BigDecimal.valueOf(fileCount).multiply(rule.getPerFileCredits()))
                .add(sourceMb.multiply(rule.getPerMbCredits()));
        long charge = total.setScale(0, RoundingMode.CEILING).longValueExact();
        return Math.max(charge, rule.getMinimumCredits());
    }
}
