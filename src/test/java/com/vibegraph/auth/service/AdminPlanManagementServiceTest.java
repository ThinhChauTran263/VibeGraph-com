package com.vibegraph.auth.service;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.entity.Plan;
import com.vibegraph.auth.dto.AdminPlanResponse;
import com.vibegraph.auth.dto.AdminPlanUpsertRequest;
import com.vibegraph.auth.repository.PlanRepository;
import com.vibegraph.auth.repository.UserAccountSettingsRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPlanManagementService")
class AdminPlanManagementServiceTest {

    @Mock private PlanRepository planRepository;
    @Mock private UserAccountSettingsRepository settingsRepository;
    @Mock private AuditService auditService;

    @Test
    @DisplayName("deactivateOrDelete deactivates an in-use plan instead of hard deleting")
    void deactivateOrDelete_inUsePlan_deactivatesOnly() {
        Plan plan = Plan.builder().code("PRO").isActive(true).build();
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(settingsRepository.countByPlan_Code("PRO")).thenReturn(2L);
        AdminPlanManagementService service = new AdminPlanManagementService(
                planRepository, settingsRepository, auditService);

        service.deactivateOrDelete("PRO");

        assertFalse(plan.isActive());
        verify(planRepository).save(plan);
        verify(planRepository, never()).delete(plan);
        verify(auditService).recordCurrentUser("PLAN_DEACTIVATE", null, "PLAN", "PRO",
                java.util.Map.of("operation", "DEACTIVATE", "active", false));
    }

    @Test
    @DisplayName("create converts admin MB quota to bytes and returns MB")
    void create_usesMegabytesAtTheApiBoundary() {
        AdminPlanUpsertRequest request = new AdminPlanUpsertRequest(
                "PRO", "Pro", 500, 10, 500, false, true, 20);
        Plan saved = Plan.builder()
                .code("PRO")
                .name("Pro")
                .storageLimitBytes(524_288_000L)
                .apiKeyLimit(10)
                .monthlyCreditLimit(500)
                .build();
        when(planRepository.existsByCode("PRO")).thenReturn(false);
        when(planRepository.save(org.mockito.ArgumentMatchers.any(Plan.class))).thenReturn(saved);

        AdminPlanResponse response = new AdminPlanManagementService(
                planRepository, settingsRepository, auditService).create(request);

        org.junit.jupiter.api.Assertions.assertEquals(500, response.storageLimitMb());
        verify(planRepository).save(org.mockito.ArgumentMatchers.argThat(
                plan -> plan.getStorageLimitBytes() == 524_288_000L));
        verify(auditService).recordCurrentUser(
                org.mockito.ArgumentMatchers.eq("PLAN_CREATE"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("PLAN"),
                org.mockito.ArgumentMatchers.eq("PRO"),
                org.mockito.ArgumentMatchers.argThat(details -> details.get("storageLimitMb").equals(500L)));
    }

    @Test
    @DisplayName("deactivateOrDelete audits hard deletion for an unused plan")
    void deactivateOrDelete_unusedPlan_deletesAndAudits() {
        Plan plan = Plan.builder().code("LEGACY").build();
        when(planRepository.findByCode("LEGACY")).thenReturn(Optional.of(plan));
        when(settingsRepository.countByPlan_Code("LEGACY")).thenReturn(0L);
        AdminPlanManagementService service = new AdminPlanManagementService(
                planRepository, settingsRepository, auditService);

        service.deactivateOrDelete("LEGACY");

        verify(planRepository).delete(plan);
        verify(auditService).recordCurrentUser("PLAN_DELETE", null, "PLAN", "LEGACY",
                java.util.Map.of("operation", "DELETE"));
    }
}
