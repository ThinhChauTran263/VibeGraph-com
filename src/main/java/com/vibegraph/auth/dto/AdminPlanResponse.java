package com.vibegraph.auth.dto;

import com.vibegraph.auth.domain.Plan;

public record AdminPlanResponse(
        String code,
        String name,
        long storageLimitBytes,
        int apiKeyLimit,
        int monthlyCreditLimit,
        boolean contactSalesRequired) {

    public static AdminPlanResponse from(Plan plan) {
        return new AdminPlanResponse(
                plan.getCode(),
                plan.getName(),
                plan.getStorageLimitBytes(),
                plan.getApiKeyLimit(),
                plan.getMonthlyCreditLimit(),
                plan.isContactSalesRequired());
    }
}
