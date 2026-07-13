package com.vibegraph.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.CreditPricingRule;
import com.vibegraph.auth.repository.CreditPricingRuleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to calculate required credits for a specific operation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditPricingService {

    private final CreditPricingRuleRepository pricingRuleRepository;

    /**
     * Calculate credits based on the pricing rules for the given operation.
     * Uses Math.ceil to round up.
     */
    @Transactional(readOnly = true)
    public long calculateCredits(String operationCode, int fileCount, long sourceMb, long nodeCount) {
        CreditPricingRule rule = pricingRuleRepository.findByOperationCodeAndActiveTrue(operationCode)
                .orElse(null);
                
        if (rule == null) {
            log.warn("No active pricing rule found for operation: {}", operationCode);
            return 0; // Or throw an exception depending on business rules, but returning 0 allows free operations if unconfigured.
        }

        long calculated = rule.getBaseCredits()
                + (fileCount * rule.getPerFileCredits())
                + (sourceMb * rule.getPerMbCredits())
                + ((long) Math.ceil(nodeCount / 1000.0) * rule.getPer1kNodesCredits());

        return Math.max(rule.getMinimumCredits(), calculated);
    }
}
