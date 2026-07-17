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

import com.vibegraph.auth.dto.NotificationResponse;
import com.vibegraph.auth.service.NotificationService;
import com.vibegraph.common.dto.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountNotificationController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.list(limit)));
    }

    /** Backward-compatible alias used by the current web shell. */
    @GetMapping("/announcements")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> announcements(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.list(limit)));
    }

    @GetMapping("/notifications/{id}")
    public ResponseEntity<ApiResponse<NotificationResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.get(id)));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markRead(id)));
    }

    @PatchMapping("/notifications/{id}/dismiss")
    public ResponseEntity<ApiResponse<NotificationResponse>> dismiss(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.dismiss(id)));
    }
}
