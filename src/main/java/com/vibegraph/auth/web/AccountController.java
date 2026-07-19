package com.vibegraph.auth.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.AccountCreditLedgerResponse;
import com.vibegraph.auth.dto.AccountProfileUpdateRequest;
import com.vibegraph.auth.dto.AccountPasswordChangeRequest;
import com.vibegraph.auth.dto.AccountProjectPageRequest;
import com.vibegraph.auth.dto.AccountProjectsPageResponse;
import com.vibegraph.auth.dto.AccountSessionStateResponse;
import com.vibegraph.auth.dto.AccountUsageResponse;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.service.AccountService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> profile() {
        return ResponseEntity.ok(ApiResponse.success(accountService.profile()));
    }

    @GetMapping("/session-state")
    public ResponseEntity<ApiResponse<AccountSessionStateResponse>> sessionState() {
        return ResponseEntity.ok(ApiResponse.success(accountService.sessionState()));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody AccountProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountService.updateProfile(request)));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody AccountPasswordChangeRequest request) {
        accountService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<AccountUsageResponse>> usage() {
        return ResponseEntity.ok(ApiResponse.success(accountService.usage()));
    }

    @GetMapping("/usage/ledger")
    public ResponseEntity<ApiResponse<List<AccountCreditLedgerResponse>>> creditLedger(
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 50, message = "limit must be at most 50") int limit) {
        validateLimit(limit);
        return ResponseEntity.ok(ApiResponse.success(accountService.creditLedger(limit)));
    }

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<AccountProjectsPageResponse>> projects(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be at least 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") int size) {
        validatePagination(page, size);
        return ResponseEntity.ok(ApiResponse.success(accountService.projects(new AccountProjectPageRequest(page, size))));
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ConstraintViolationException("page must be at least 0", java.util.Set.of());
        }
        if (size < 1 || size > 100) {
            throw new ConstraintViolationException("size must be between 1 and 100", java.util.Set.of());
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 50) {
            throw new ConstraintViolationException("limit must be between 1 and 50", java.util.Set.of());
        }
    }
}
