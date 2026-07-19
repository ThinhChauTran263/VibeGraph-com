package com.vibegraph.auth.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.AdminStorageOverviewResponse;
import com.vibegraph.auth.service.AdminStorageService;
import com.vibegraph.common.dto.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/storage")
@RequiredArgsConstructor
public class AdminStorageController {

    private final AdminStorageService storageService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminStorageOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.success(storageService.overview()));
    }
}
