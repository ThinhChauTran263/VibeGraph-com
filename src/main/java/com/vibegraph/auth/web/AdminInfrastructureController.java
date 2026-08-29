package com.vibegraph.auth.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot;
import com.vibegraph.infrastructure.service.InfrastructureEventStream;
import com.vibegraph.infrastructure.service.InfrastructureMetricsService;
import com.vibegraph.infrastructure.service.OperationTelemetryRecorder;

import lombok.RequiredArgsConstructor;

/** Admin-only VPS metrics and bounded operation evidence endpoints. */
@RestController
@RequestMapping("/api/admin/infrastructure")
@RequiredArgsConstructor
public class AdminInfrastructureController {

    private final InfrastructureMetricsService metricsService;
    private final InfrastructureEventStream eventStream;
    private final OperationTelemetryRecorder telemetryRecorder;

    @GetMapping
    public ResponseEntity<ApiResponse<InfrastructureSnapshot>> snapshot() {
        return ResponseEntity.ok(ApiResponse.success(metricsService.snapshot()));
    }

    @GetMapping("/operations")
    public ResponseEntity<ApiResponse<List<InfrastructureSnapshot.OperationEvidence>>> operations(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "ALL") String type) {
        String normalized = type == null ? "ALL" : type.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("ALL", "ANALYZE", "MCP", "API", "IMPORT", "OTHER").contains(normalized)) {
            throw new IllegalArgumentException("type must be one of ALL, ANALYZE, MCP, API, IMPORT, OTHER");
        }
        return ResponseEntity.ok(ApiResponse.success(telemetryRecorder.recent(limit, normalized)));
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return eventStream.subscribe();
    }
}
