package com.vibegraph.auth.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.dto.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Plan>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getPlans()));
    }
}
