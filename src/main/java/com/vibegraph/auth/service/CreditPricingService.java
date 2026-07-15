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
 */
@Service
@RequiredArgsConstructor
public class CreditPricingService {

    private static final long BYTES_PER_MIB = 1_048_576L;

    private final CreditPricingRuleRepository pricingRuleRepository;

    @Transactional(readOnly = true)
    public long calculateCredits(String operationCode, int fileCount, long sourceBytes) {
        return calculateCredits(operationCode, fileCount, sourceBytes, 0, false);
    }

    @Transactional(readOnly = true)
    public long calculateCredits(
            String operationCode,
            int fileCount,
            long sourceBytes,
            long nodeCount) {
        return calculateCredits(operationCode, fileCount, sourceBytes, nodeCount, true);
    }

    private long calculateCredits(
            String operationCode,
            int fileCount,
            long sourceBytes,
            long nodeCount,
            boolean applyNodeAndMinimumPricing) {
        if (fileCount < 0) {
            throw new IllegalArgumentException("fileCount must be non-negative");
        }
        if (sourceBytes < 0) {
            throw new IllegalArgumentException("sourceBytes must be non-negative");
        }
        if (nodeCount < 0) {
            throw new IllegalArgumentException("nodeCount must be non-negative");
        }

        CreditPricingRule rule = pricingRuleRepository.findByOperationCode(operationCode)
                .filter(CreditPricingRule::isActive)
                .orElseThrow(() -> new IllegalStateException(
                        "No active pricing rule found for operation: " + operationCode));

        long sourceMb = sourceBytes == 0
                ? 0
                : Math.floorDiv(sourceBytes - 1, BYTES_PER_MIB) + 1;
        BigDecimal total = rule.getBaseCredits()
                .add(BigDecimal.valueOf(fileCount).multiply(rule.getPerFileCredits()))
                .add(BigDecimal.valueOf(sourceMb).multiply(rule.getPerMbCredits()));
        if (applyNodeAndMinimumPricing) {
            long nodeUnits = nodeCount == 0 ? 0 : Math.floorDiv(nodeCount - 1, 1_000) + 1;
            total = total.add(BigDecimal.valueOf(nodeUnits).multiply(rule.getPer1kNodesCredits()));
        }
        long rounded = total.setScale(0, RoundingMode.CEILING).longValueExact();
        return applyNodeAndMinimumPricing ? Math.max(rule.getMinimumCredits(), rounded) : rounded;
    }
}
