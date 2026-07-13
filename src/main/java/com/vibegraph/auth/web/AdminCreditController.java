package com.vibegraph.auth.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.AdminCreditAdjustmentRequest;
import com.vibegraph.auth.dto.AdminCreditOverviewResponse;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/credits")
@RequiredArgsConstructor
public class AdminCreditController {

    private final AdminService adminService;

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminCreditOverviewResponse>> getOverview(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getCreditOverview(userId)));
    }

    @PostMapping("/users/{userId}/adjust")
    public ResponseEntity<ApiResponse<Void>> adjustCredits(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminCreditAdjustmentRequest request
    ) {
        adminService.adjustCredits(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
