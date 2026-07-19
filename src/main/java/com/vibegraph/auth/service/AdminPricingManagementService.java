package com.vibegraph.auth.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.CreditPricingRule;
import com.vibegraph.auth.dto.AdminPricingRuleResponse;
import com.vibegraph.auth.dto.AdminPricingRuleUpsertRequest;
import com.vibegraph.auth.repository.CreditPricingRuleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminPricingManagementService {

    private final CreditPricingRuleRepository pricingRuleRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AdminPricingRuleResponse> list() {
        return pricingRuleRepository.findAll().stream()
                .map(AdminPricingRuleResponse::from)
                .toList();
    }

    @Transactional
    public AdminPricingRuleResponse create(AdminPricingRuleUpsertRequest request) {
        if (pricingRuleRepository.existsByOperationCode(request.operationCode())) {
            throw new IllegalArgumentException("Pricing rule already exists");
        }
        AdminPricingRuleResponse response = AdminPricingRuleResponse.from(pricingRuleRepository.save(
                toRule(CreditPricingRule.builder().build(), request)));
        auditService.recordCurrentUser("PRICING_RULE_CREATE", null, "PRICING_RULE",
                response.operationCode(), pricingDetails(response));
        return response;
    }

    @Transactional
    public AdminPricingRuleResponse update(String operationCode, AdminPricingRuleUpsertRequest request) {
        if (!operationCode.equals(request.operationCode())) {
            throw new IllegalArgumentException("Operation code cannot be changed");
        }
        CreditPricingRule rule = pricingRuleRepository.findByOperationCode(operationCode)
                .orElseThrow(() -> new IllegalArgumentException("Pricing rule not found: " + operationCode));
        AdminPricingRuleResponse response = AdminPricingRuleResponse.from(
                pricingRuleRepository.save(toRule(rule, request)));
        auditService.recordCurrentUser("PRICING_RULE_UPDATE", null, "PRICING_RULE",
                operationCode, pricingDetails(response));
        return response;
    }

    @Transactional
    public void deactivate(String operationCode) {
        CreditPricingRule rule = pricingRuleRepository.findByOperationCode(operationCode)
                .orElseThrow(() -> new IllegalArgumentException("Pricing rule not found: " + operationCode));
        rule.setActive(false);
        pricingRuleRepository.save(rule);
        auditService.recordCurrentUser("PRICING_RULE_DEACTIVATE", null, "PRICING_RULE", operationCode,
                java.util.Map.of("active", false));
    }

    private java.util.Map<String, Object> pricingDetails(AdminPricingRuleResponse response) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("baseCredits", response.baseCredits());
        details.put("perFileCredits", response.perFileCredits());
        details.put("perMbCredits", response.perMbCredits());
        details.put("per1kNodesCredits", response.per1kNodesCredits());
        details.put("minimumCredits", response.minimumCredits());
        details.put("active", response.active());
        return details;
    }

    private CreditPricingRule toRule(CreditPricingRule rule, AdminPricingRuleUpsertRequest request) {
        rule.setOperationCode(request.operationCode());
        rule.setDisplayName(request.displayName());
        rule.setBaseCredits(defaultZero(request.baseCredits()));
        rule.setPerFileCredits(defaultZero(request.perFileCredits()));
        rule.setPerMbCredits(defaultZero(request.perMbCredits()));
        rule.setPer1kNodesCredits(defaultZero(request.per1kNodesCredits()));
        rule.setMinimumCredits(request.minimumCredits());
        rule.setActive(request.active());
        return rule;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
