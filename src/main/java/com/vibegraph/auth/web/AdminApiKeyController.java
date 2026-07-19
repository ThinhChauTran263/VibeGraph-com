package com.vibegraph.auth.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.ApiKeyResponse;
import com.vibegraph.auth.service.ApiKeyService;
import com.vibegraph.common.dto.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/api-keys")
@RequiredArgsConstructor
public class AdminApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyResponse>>> list(@RequestParam UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(apiKeyService.listForUser(userId)));
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<Void>> disable(@PathVariable UUID id) {
        apiKeyService.disableForAnyUser(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<Void>> lock(@PathVariable UUID id) {
        apiKeyService.disableForAnyUser(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<Void>> unlock(@PathVariable UUID id) {
        apiKeyService.unlockForAnyUser(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
