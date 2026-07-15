package com.vibegraph.auth.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.SecurityEventResponse;
import com.vibegraph.auth.service.AdminSecurityMonitorService;
import com.vibegraph.common.dto.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
public class AdminSecurityMonitorController {

    private final AdminSecurityMonitorService securityMonitorService;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<SecurityEventResponse>>> events(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(securityMonitorService.recentEvents(limit)));
    }
}
