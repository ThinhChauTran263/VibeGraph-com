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

import com.vibegraph.auth.dto.AdminPlanResponse;
import com.vibegraph.auth.dto.AdminPlanUpsertRequest;
import com.vibegraph.auth.service.AdminPlanManagementService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {

    private final AdminPlanManagementService planManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminPlanResponse>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(planManagementService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminPlanResponse>> create(@Valid @RequestBody AdminPlanUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(planManagementService.create(request)));
    }

    @PutMapping("/{code}")
    public ResponseEntity<ApiResponse<AdminPlanResponse>> update(
            @PathVariable String code,
            @Valid @RequestBody AdminPlanUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.success(planManagementService.update(code, request)));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<ApiResponse<Void>> deactivateOrDelete(@PathVariable String code) {
        planManagementService.deactivateOrDelete(code);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
