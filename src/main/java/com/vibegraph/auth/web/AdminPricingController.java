package com.vibegraph.auth.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.AdminPricingRuleResponse;
import com.vibegraph.auth.dto.AdminPricingRuleUpsertRequest;
import com.vibegraph.auth.service.AdminPricingManagementService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/pricing-rules")
@RequiredArgsConstructor
public class AdminPricingController {

    private final AdminPricingManagementService pricingManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminPricingRuleResponse>>> getPricingRules() {
        return ResponseEntity.ok(ApiResponse.success(pricingManagementService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminPricingRuleResponse>> create(
            @Valid @RequestBody AdminPricingRuleUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(pricingManagementService.create(request)));
    }

    @PutMapping("/{operationCode}")
    public ResponseEntity<ApiResponse<AdminPricingRuleResponse>> update(
            @PathVariable String operationCode,
            @Valid @RequestBody AdminPricingRuleUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pricingManagementService.update(operationCode, request)));
    }

    @DeleteMapping("/{operationCode}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable String operationCode) {
        pricingManagementService.deactivate(operationCode);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
