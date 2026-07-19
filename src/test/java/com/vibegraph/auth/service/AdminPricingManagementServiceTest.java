package com.vibegraph.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.CreditPricingRule;
import com.vibegraph.auth.dto.AdminPricingRuleUpsertRequest;
import com.vibegraph.auth.repository.CreditPricingRuleRepository;

@ExtendWith(MockitoExtension.class)
class AdminPricingManagementServiceTest {

    @Mock private CreditPricingRuleRepository repository;
    @Mock private AuditService auditService;

    @Test
    void create_persistsAndAuditsPricingRule() {
        AdminPricingRuleUpsertRequest request = request("IMPORT");
        when(repository.existsByOperationCode("IMPORT")).thenReturn(false);
        when(repository.save(any(CreditPricingRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminPricingManagementService service = new AdminPricingManagementService(repository, auditService);

        service.create(request);

        verify(auditService).recordCurrentUser(
                eq("PRICING_RULE_CREATE"), eq(null), eq("PRICING_RULE"), eq("IMPORT"), anyMap());
    }

    private AdminPricingRuleUpsertRequest request(String code) {
        return new AdminPricingRuleUpsertRequest(code, "Import", BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 1, true);
    }

    @Test
    void update_existingRule_auditsUpdatedPricing() {
        CreditPricingRule rule = CreditPricingRule.builder().operationCode("IMPORT").build();
        when(repository.findByOperationCode("IMPORT")).thenReturn(Optional.of(rule));
        when(repository.save(rule)).thenReturn(rule);
        AdminPricingManagementService service = new AdminPricingManagementService(repository, auditService);

        service.update("IMPORT", request("IMPORT"));

        verify(auditService).recordCurrentUser(
                eq("PRICING_RULE_UPDATE"), eq(null), eq("PRICING_RULE"), eq("IMPORT"), anyMap());
    }

    @Test
    void deactivate_existingRule_auditsDeactivation() {
        CreditPricingRule rule = CreditPricingRule.builder().operationCode("IMPORT").isActive(true).build();
        when(repository.findByOperationCode("IMPORT")).thenReturn(Optional.of(rule));
        AdminPricingManagementService service = new AdminPricingManagementService(repository, auditService);

        service.deactivate("IMPORT");

        verify(repository).save(rule);
        verify(auditService).recordCurrentUser(
                "PRICING_RULE_DEACTIVATE", null, "PRICING_RULE", "IMPORT", java.util.Map.of("active", false));
    }
}
