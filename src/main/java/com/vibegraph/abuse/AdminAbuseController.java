package com.vibegraph.abuse;

import com.vibegraph.abuse.entity.IpBlock;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.service.AdminSecurityMonitorService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/security")
public class AdminAbuseController {

    private final AdminSecurityMonitorService securityMonitorService;
    private final IpBlockService ipBlockService;

    public AdminAbuseController(AdminSecurityMonitorService securityMonitorService, IpBlockService ipBlockService) {
        this.securityMonitorService = securityMonitorService;
        this.ipBlockService = ipBlockService;
    }

    @GetMapping("/request-events")
    public ResponseEntity<ApiResponse<List<RequestEventResponse>>> requestEvents(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success(securityMonitorService.requestEvents(limit)));
    }

    @GetMapping("/top-users")
    public ResponseEntity<ApiResponse<List<RequestAggregateResponse>>> topUsers(
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(securityMonitorService.topUsers(minutes, limit)));
    }

    @GetMapping("/top-ips")
    public ResponseEntity<ApiResponse<List<RequestAggregateResponse>>> topIps(
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(securityMonitorService.topIps(minutes, limit)));
    }

    @GetMapping("/suspicious-networks")
    public ResponseEntity<ApiResponse<List<SuspiciousNetworkResponse>>> suspiciousNetworks(
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(securityMonitorService.suspiciousNetworks(minutes, limit)));
    }

    @GetMapping("/ip-blocks")
    public ResponseEntity<ApiResponse<List<IpBlockResponse>>> ipBlocks(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success(ipBlockService.list(limit).stream().map(IpBlockResponse::from).toList()));
    }

    @PostMapping("/ip-blocks")
    public ResponseEntity<ApiResponse<IpBlockResponse>> createIpBlock(@Valid @RequestBody IpBlockRequest request) {
        IpBlock created = ipBlockService.create(request.ipAddress(), request.safeReason(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(IpBlockResponse.from(created)));
    }

    @PatchMapping("/ip-blocks/{id}")
    public ResponseEntity<ApiResponse<IpBlockResponse>> updateIpBlock(@PathVariable UUID id,
            @Valid @RequestBody IpBlockRequest request) {
        IpBlock updated = ipBlockService.update(id, request.ipAddress(), request.safeReason(), request.expiresAt(), request.active());
        return ResponseEntity.ok(ApiResponse.success(IpBlockResponse.from(updated)));
    }

    @DeleteMapping("/ip-blocks/{id}")
    public ResponseEntity<Void> removeIpBlock(@PathVariable UUID id) {
        ipBlockService.remove(id);
        return ResponseEntity.noContent().build();
    }
}
