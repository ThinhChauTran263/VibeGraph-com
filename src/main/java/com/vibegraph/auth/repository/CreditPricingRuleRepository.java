package com.vibegraph.auth.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vibegraph.auth.domain.CreditPricingRule;

public interface CreditPricingRuleRepository extends JpaRepository<CreditPricingRule, UUID> {
    Optional<CreditPricingRule> findByOperationCodeAndActiveTrue(String operationCode);
}
