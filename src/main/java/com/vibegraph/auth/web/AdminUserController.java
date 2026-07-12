package com.vibegraph.auth.web;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.AdminCreateUserRequest;
import com.vibegraph.auth.dto.AdminUserBlockRequest;
import com.vibegraph.auth.dto.AdminUserResponse;
import com.vibegraph.auth.dto.AdminUserUpdatePlanRequest;
import com.vibegraph.auth.dto.AdminUserUpdateQuotaRequest;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<ApiResponse<com.vibegraph.auth.dto.AdminPageResponse<AdminUserResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plan,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("email").ascending());
        Page<AdminUserResponse> result = adminService.getUsers(search, status, plan, pageable);
        var pageResponse = new com.vibegraph.auth.dto.AdminPageResponse<>(
                result.getContent(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getDetail(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getUserDetail(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminUserResponse>> create(
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminService.createUser(request)));
    }

    @PatchMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<AdminUserResponse>> block(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserBlockRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.blockUser(userId, request)));
    }

    @PatchMapping("/{userId}/unblock")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unblock(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.unblockUser(userId)));
    }

    @PatchMapping("/{userId}/deactivate")
    public ResponseEntity<ApiResponse<AdminUserResponse>> deactivate(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.deactivateUser(userId)));
    }

    @PatchMapping("/{userId}/plan")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updatePlan(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserUpdatePlanRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.updatePlan(userId, request)));
    }

    @PatchMapping("/{userId}/quota")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateQuota(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserUpdateQuotaRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.updateQuota(userId, request)));
    }

    @PatchMapping("/{userId}/api-key-creation")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateApiKeyCreation(
            @PathVariable UUID userId,
            @Valid @RequestBody com.vibegraph.auth.dto.AdminApiKeyCreationToggleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.updateApiKeyCreationDisabled(userId, request.disabled())));
    }
}
