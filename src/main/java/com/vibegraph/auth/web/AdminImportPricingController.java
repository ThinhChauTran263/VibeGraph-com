package com.vibegraph.auth.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.AdminImportPricingResponse;
import com.vibegraph.auth.dto.AdminImportPricingUpdateRequest;
import com.vibegraph.auth.service.AdminImportPricingManagementService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Tiered import pricing management (small/medium/large/xlarge), one tier set
 * per import method. Secured by the {@code /api/admin/**} ADMIN-role rule.
 */
@RestController
@RequestMapping("/api/admin/import-pricing")
@RequiredArgsConstructor
public class AdminImportPricingController {

    private final AdminImportPricingManagementService pricingManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminImportPricingResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(pricingManagementService.listAll()));
    }

    @GetMapping("/{operationCode}")
    public ResponseEntity<ApiResponse<AdminImportPricingResponse>> get(@PathVariable String operationCode) {
        return ResponseEntity.ok(ApiResponse.success(pricingManagementService.get(operationCode)));
    }

    @PutMapping("/{operationCode}")
    public ResponseEntity<ApiResponse<AdminImportPricingResponse>> replace(
            @PathVariable String operationCode,
            @Valid @RequestBody AdminImportPricingUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(pricingManagementService.replace(operationCode, request)));
    }
}
