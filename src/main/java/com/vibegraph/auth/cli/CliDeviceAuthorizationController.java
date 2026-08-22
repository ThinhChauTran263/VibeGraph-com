package com.vibegraph.auth.cli;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Public device start/exchange and browser-authenticated approval endpoints. */
@RestController
@RequestMapping("/api/cli/device")
@RequiredArgsConstructor
public class CliDeviceAuthorizationController {

    private final CliDeviceAuthorizationService service;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<CliDeviceStartResponse>> start(
            @Valid @RequestBody CliDeviceStartRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.start(request)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<CliDeviceApprovalResponse>> approve(
            @PathVariable UUID id,
            @Valid @RequestBody CliDeviceApprovalRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.approve(id, request)));
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<CliDeviceTokenResponse>> token(
        @Valid @RequestBody CliDeviceTokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.exchange(
                        request.deviceCode(), request.pollToken(), request.codeVerifier())));
    }

    @PostMapping("/status")
    public ResponseEntity<ApiResponse<CliDeviceTokenResponse>> status(
            @Valid @RequestBody CliDeviceStatusRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.status(request.deviceCode(), request.pollToken())));
    }
}
