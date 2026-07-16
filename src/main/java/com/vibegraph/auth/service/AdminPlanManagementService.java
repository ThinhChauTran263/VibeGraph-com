package com.vibegraph.auth.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.Plan;
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
        return AdminPlanResponse.from(planRepository.save(toPlan(Plan.builder().build(), request)));
    }

    @Transactional
    public AdminPlanResponse update(String code, AdminPlanUpsertRequest request) {
        if (!code.equals(request.code())) {
            throw new IllegalArgumentException("Plan code cannot be changed");
        }
        Plan plan = planRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + code));
        return AdminPlanResponse.from(planRepository.save(toPlan(plan, request)));
    }

    @Transactional
    public void deactivateOrDelete(String code) {
        Plan plan = planRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + code));
        if (settingsRepository.countByPlan_Code(code) > 0) {
            plan.setActive(false);
            planRepository.save(plan);
            return;
        }
        planRepository.delete(plan);
    }

    private Plan toPlan(Plan plan, AdminPlanUpsertRequest request) {
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setStorageLimitBytes(request.storageLimitBytes());
        plan.setApiKeyLimit(request.apiKeyLimit());
        plan.setMonthlyCreditLimit(request.monthlyCreditLimit());
        plan.setContactSalesRequired(request.contactSalesRequired());
        plan.setActive(request.active());
        plan.setSortOrder(request.sortOrder());
        return plan;
    }
}
