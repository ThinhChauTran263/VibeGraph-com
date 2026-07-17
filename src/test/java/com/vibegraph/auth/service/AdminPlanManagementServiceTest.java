package com.vibegraph.auth.service;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.Plan;
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

    @Test
    @DisplayName("deactivateOrDelete deactivates an in-use plan instead of hard deleting")
    void deactivateOrDelete_inUsePlan_deactivatesOnly() {
        Plan plan = Plan.builder().code("PRO").isActive(true).build();
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(settingsRepository.countByPlan_Code("PRO")).thenReturn(2L);
        AdminPlanManagementService service = new AdminPlanManagementService(planRepository, settingsRepository);

        service.deactivateOrDelete("PRO");

        assertFalse(plan.isActive());
        verify(planRepository).save(plan);
        verify(planRepository, never()).delete(plan);
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
                planRepository, settingsRepository).create(request);

        org.junit.jupiter.api.Assertions.assertEquals(500, response.storageLimitMb());
        verify(planRepository).save(org.mockito.ArgumentMatchers.argThat(
                plan -> plan.getStorageLimitBytes() == 524_288_000L));
    }
}
