package com.vibegraph.auth.web;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.AdminPageResponse;
import com.vibegraph.auth.dto.AuditLogResponse;
import com.vibegraph.auth.dto.AuditRetentionResponse;
import com.vibegraph.auth.dto.AuditRetentionUpdateRequest;
import com.vibegraph.auth.service.AuditService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
public class AdminAuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminPageResponse<AuditLogResponse>>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID targetUserId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var result = auditService.list(
                action, outcome, actorUserId, targetUserId, from, to,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(new AdminPageResponse<>(
                result.getContent(), result.getTotalElements(), result.getTotalPages(),
                result.getNumber(), result.getSize())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditLogResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(auditService.get(id)));
    }

    @GetMapping("/retention")
    public ResponseEntity<ApiResponse<AuditRetentionResponse>> retention() {
        return ResponseEntity.ok(ApiResponse.success(auditService.getRetention()));
    }

    @PutMapping("/retention")
    public ResponseEntity<ApiResponse<AuditRetentionResponse>> updateRetention(
            @Valid @RequestBody AuditRetentionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(auditService.updateRetention(request.retentionDays())));
    }
}
