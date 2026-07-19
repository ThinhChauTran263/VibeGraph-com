package com.vibegraph.auth.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.FeatureFlagRequest;
import com.vibegraph.auth.dto.FeatureFlagResponse;
import com.vibegraph.auth.service.AdminFeatureFlagService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/feature-flags")
@RequiredArgsConstructor
public class AdminFeatureFlagController {

    private final AdminFeatureFlagService featureFlagService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FeatureFlagResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(featureFlagService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FeatureFlagResponse>> create(@Valid @RequestBody FeatureFlagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(featureFlagService.create(request)));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<FeatureFlagResponse>> update(
            @PathVariable String key,
            @Valid @RequestBody FeatureFlagRequest request) {
        return ResponseEntity.ok(ApiResponse.success(featureFlagService.update(key, request)));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String key) {
        featureFlagService.delete(key);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
