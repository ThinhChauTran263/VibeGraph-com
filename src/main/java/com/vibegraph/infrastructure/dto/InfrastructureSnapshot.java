package com.vibegraph.infrastructure.dto;

import java.time.Instant;
import java.util.List;

public record InfrastructureSnapshot(
        Instant capturedAt,
        String status,
        HostMetrics host,
        MemoryMetrics memory,
        DiskMetrics disk,
        NetworkMetrics network,
        DiskIoMetrics diskIo,
        List<ContainerMetrics> containers,
        OperationEvidence latestOperation,
        CapacityMetrics capacity,
        List<OperationEvidence> history,
        List<Incident> incidents) {

    public record HostMetrics(
            double cpuPercent,
            int vcpuCount,
            Double currentGHz,
            double avgCpuPercent,
            double peakCpuPercent,
            String source,
            String status) {
    }

    public record MemoryMetrics(
            long totalBytes,
            long usedBytes,
            long availableBytes,
            double usedPercent,
            List<ResourceBreakdown> breakdown,
            String source,
            String status) {
    }

    public record DiskMetrics(
            long totalBytes,
            long usedBytes,
            long freeBytes,
            double usedPercent,
            List<ResourceBreakdown> breakdown,
            String source,
            String status) {
    }

    public record ResourceBreakdown(
            String key,
            String label,
            long usedBytes,
            double percentOfTotal,
            String source,
            String status) {
    }

    public record NetworkMetrics(
            long inBytesPerSecond,
            long outBytesPerSecond,
            long droppedPackets,
            String source,
            String status) {
    }

    public record DiskIoMetrics(
            long readBytesPerSecond,
            long writeBytesPerSecond,
            Double utilizationPercent,
            String source,
            String status) {
    }

    public record ContainerMetrics(
            String name,
            String status,
            boolean healthy,
            Boolean healthKnown,
            String healthStatus,
            long memoryUsedBytes,
            Long memoryLimitBytes,
            double cpuPercent,
            Long restartCount,
            Long uptimeSeconds,
            String source) {
    }

    public record OperationEvidence(
            String id,
            String traceId,
            String projectId,
            String projectName,
            String type,
            String operation,
            String status,
            Instant startedAt,
            Instant completedAt,
            long durationMs,
            int nodes,
            int edges,
            long ramBeforeBytes,
            long ramPeakBytes,
            long ramIncreaseBytes,
            long ramAfterCooldownBytes,
            boolean cooldownComplete,
            double cpuAvgPercent,
            double cpuPeakPercent,
            double cpuCoreSeconds,
            long storageAddedBytes,
            long diskReadBytes,
            long diskWriteBytes,
            int concurrentHeavyOperations,
            String backendVersion,
            String measurementType,
            String confidence,
            String stopReason) {
    }

    public record CapacityMetrics(
            String status,
            int evidenceSamples,
            String confidence,
            Double safeHeadroomPercent,
            CapacityBoundary mcpSafe,
            CapacityBoundary graphApi,
            CapacityBoundary analyzeObservedSafe,
            String heavyConcurrency) {
    }

    public record CapacityBoundary(
            int nodes,
            int edges,
            String measurementType,
            String confidence,
            String evidenceId) {
    }

    public record Incident(
            String id,
            String evidenceId,
            String type,
            String severity,
            String reason,
            Double actualValue,
            Double threshold,
            Instant occurredAt,
            String projectName,
            String operationType,
            String status) {
    }
}
