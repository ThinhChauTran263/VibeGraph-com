package com.vibegraph.infrastructure.persistence.entity;

import com.vibegraph.infrastructure.persistence.OperationTelemetrySanitizer;

import java.time.Instant;

import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.OperationEvidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

/** Durable bounded operation summary. It intentionally stores no source, path, or credential data. */
@Entity
@Table(name = "infrastructure_operation_history")
@Getter
public class InfrastructureOperationHistory {

    @Id
    @Column(length = 64, nullable = false)
    private String id;
    @Column(name = "trace_id", length = 64, nullable = false)
    private String traceId;
    @Column(name = "project_id", length = 160)
    private String projectId;
    @Column(name = "project_name", length = 160)
    private String projectName;
    @Column(name = "operation_type", length = 24, nullable = false)
    private String type;
    @Column(length = 160)
    private String operation;
    @Column(length = 24, nullable = false)
    private String status;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;
    @Column(nullable = false)
    private int nodes;
    @Column(nullable = false)
    private int edges;
    @Column(name = "ram_before_bytes", nullable = false)
    private long ramBeforeBytes;
    @Column(name = "ram_peak_bytes", nullable = false)
    private long ramPeakBytes;
    @Column(name = "ram_increase_bytes", nullable = false)
    private long ramIncreaseBytes;
    @Column(name = "ram_after_cooldown_bytes", nullable = false)
    private long ramAfterCooldownBytes;
    @Column(name = "cooldown_complete", nullable = false)
    private boolean cooldownComplete;
    @Column(name = "cpu_avg_percent", nullable = false)
    private double cpuAvgPercent;
    @Column(name = "cpu_peak_percent", nullable = false)
    private double cpuPeakPercent;
    @Column(name = "cpu_core_seconds", nullable = false)
    private double cpuCoreSeconds;
    @Column(name = "storage_added_bytes", nullable = false)
    private long storageAddedBytes;
    @Column(name = "disk_read_bytes", nullable = false)
    private long diskReadBytes;
    @Column(name = "disk_write_bytes", nullable = false)
    private long diskWriteBytes;
    @Column(name = "concurrent_heavy_operations", nullable = false)
    private int concurrentHeavyOperations;
    @Column(name = "backend_version", length = 80)
    private String backendVersion;
    @Column(name = "measurement_type", length = 32)
    private String measurementType;
    @Column(length = 16)
    private String confidence;
    @Column(name = "stop_reason", length = 160)
    private String stopReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InfrastructureOperationHistory() {
    }

    public static InfrastructureOperationHistory from(OperationEvidence evidence) {
        if (evidence == null || evidence.id() == null) throw new IllegalArgumentException("evidence id is required");
        InfrastructureOperationHistory row = new InfrastructureOperationHistory();
        row.id = required(evidence.id(), 64);
        row.traceId = required(evidence.traceId(), 64);
        row.projectId = OperationTelemetrySanitizer.text(evidence.projectId(), 160);
        row.projectName = OperationTelemetrySanitizer.text(evidence.projectName(), 160);
        row.type = OperationTelemetrySanitizer.type(evidence.type());
        row.operation = OperationTelemetrySanitizer.text(evidence.operation(), 160);
        row.status = OperationTelemetrySanitizer.status(evidence.status());
        row.startedAt = evidence.startedAt() == null ? Instant.EPOCH : evidence.startedAt();
        row.completedAt = evidence.completedAt();
        row.durationMs = nonNegative(evidence.durationMs());
        row.nodes = Math.max(0, evidence.nodes());
        row.edges = Math.max(0, evidence.edges());
        row.ramBeforeBytes = nonNegative(evidence.ramBeforeBytes());
        row.ramPeakBytes = nonNegative(evidence.ramPeakBytes());
        row.ramIncreaseBytes = nonNegative(evidence.ramIncreaseBytes());
        row.ramAfterCooldownBytes = nonNegative(evidence.ramAfterCooldownBytes());
        row.cooldownComplete = evidence.cooldownComplete();
        row.cpuAvgPercent = finite(evidence.cpuAvgPercent());
        row.cpuPeakPercent = finite(evidence.cpuPeakPercent());
        row.cpuCoreSeconds = finite(evidence.cpuCoreSeconds());
        row.storageAddedBytes = nonNegative(evidence.storageAddedBytes());
        row.diskReadBytes = nonNegative(evidence.diskReadBytes());
        row.diskWriteBytes = nonNegative(evidence.diskWriteBytes());
        row.concurrentHeavyOperations = Math.max(0, evidence.concurrentHeavyOperations());
        row.backendVersion = OperationTelemetrySanitizer.text(evidence.backendVersion(), 80);
        row.measurementType = OperationTelemetrySanitizer.text(evidence.measurementType(), 32);
        row.confidence = OperationTelemetrySanitizer.text(evidence.confidence(), 16);
        row.stopReason = OperationTelemetrySanitizer.text(evidence.stopReason(), 160);
        return row;
    }

    public void applyCooldown(long ramAfterBytes) {
        ramAfterCooldownBytes = nonNegative(ramAfterBytes);
        cooldownComplete = true;
        updatedAt = Instant.now();
    }

    public OperationEvidence toEvidence() {
        return new OperationEvidence(id, traceId, projectId, projectName, type, operation, status, startedAt,
                completedAt, durationMs, nodes, edges, ramBeforeBytes, ramPeakBytes, ramIncreaseBytes,
                ramAfterCooldownBytes, cooldownComplete, cpuAvgPercent, cpuPeakPercent, cpuCoreSeconds,
                storageAddedBytes, diskReadBytes, diskWriteBytes, concurrentHeavyOperations, backendVersion,
                measurementType, confidence, stopReason);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? now : updatedAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String required(String value, int max) {
        String normalized = OperationTelemetrySanitizer.text(value, max);
        return normalized == null ? "unknown" : normalized;
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
