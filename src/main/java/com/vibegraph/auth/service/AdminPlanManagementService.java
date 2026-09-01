package com.vibegraph.auth.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.entity.Plan;
import com.vibegraph.auth.dto.AdminPlanResponse;
import com.vibegraph.auth.dto.AdminPlanUpsertRequest;
import com.vibegraph.auth.repository.PlanRepository;
import com.vibegraph.auth.repository.UserAccountSettingsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminPlanManagementService {

    private final PlanRepository planRepository;
    private final UserAccountSettingsRepository settingsRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AdminPlanResponse> list() {
        return planRepository.findAll().stream()
                .map(AdminPlanResponse::from)
                .toList();
    }

    @Transactional
    public AdminPlanResponse create(AdminPlanUpsertRequest request) {
        if (planRepository.existsByCode(request.code())) {
            throw new IllegalArgumentException("Plan code already exists");
        }
        AdminPlanResponse response = AdminPlanResponse.from(
                planRepository.save(toPlan(Plan.builder().build(), request)));
        auditService.recordCurrentUser(
                "PLAN_CREATE", null, "PLAN", response.code(), planDetails(response, request));
        return response;
    }

    @Transactional
    public AdminPlanResponse update(String code, AdminPlanUpsertRequest request) {
        if (!code.equals(request.code())) {
            throw new IllegalArgumentException("Plan code cannot be changed");
        }
        Plan plan = planRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + code));
        AdminPlanResponse response = AdminPlanResponse.from(planRepository.save(toPlan(plan, request)));
        auditService.recordCurrentUser(
                "PLAN_UPDATE", null, "PLAN", code, planDetails(response, request));
        return response;
    }

    @Transactional
    public void deactivateOrDelete(String code) {
        Plan plan = planRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + code));
        if (settingsRepository.countByPlan_Code(code) > 0) {
            plan.setActive(false);
            planRepository.save(plan);
            auditService.recordCurrentUser("PLAN_DEACTIVATE", null, "PLAN", code,
                    java.util.Map.of("operation", "DEACTIVATE", "active", false));
            return;
        }
        planRepository.delete(plan);
        auditService.recordCurrentUser("PLAN_DELETE", null, "PLAN", code,
                java.util.Map.of("operation", "DELETE"));
    }

    private java.util.Map<String, Object> planDetails(
            AdminPlanResponse response, AdminPlanUpsertRequest request) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("storageLimitMb", response.storageLimitMb());
        details.put("apiKeyLimit", response.apiKeyLimit());
        details.put("monthlyCreditLimit", response.monthlyCreditLimit());
        details.put("contactSalesRequired", response.contactSalesRequired());
        details.put("active", request.active());
        details.put("sortOrder", request.sortOrder());
        return details;
    }

    private Plan toPlan(Plan plan, AdminPlanUpsertRequest request) {
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setStorageLimitBytes(StorageUnitConverter.mbToBytes(request.storageLimitMb()));
        plan.setApiKeyLimit(request.apiKeyLimit());
        plan.setMonthlyCreditLimit(request.monthlyCreditLimit());
        plan.setContactSalesRequired(request.contactSalesRequired());
        plan.setActive(request.active());
        plan.setSortOrder(request.sortOrder());
        return plan;
    }
}
