package com.vibegraph.auth.dto;

import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.service.StorageUnitConverter;

public record AdminPlanResponse(
        String code,
        String name,
        long storageLimitMb,
        int apiKeyLimit,
        int monthlyCreditLimit,
        boolean contactSalesRequired) {

    public static AdminPlanResponse from(Plan plan) {
        return new AdminPlanResponse(
                plan.getCode(),
                plan.getName(),
                StorageUnitConverter.bytesToAvailableMb(plan.getStorageLimitBytes()),
                plan.getApiKeyLimit(),
                plan.getMonthlyCreditLimit(),
                plan.isContactSalesRequired());
    }
}
