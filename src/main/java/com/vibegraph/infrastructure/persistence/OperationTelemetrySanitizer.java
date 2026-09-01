package com.vibegraph.infrastructure.persistence;

import java.util.Locale;
import java.util.regex.Pattern;

import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.OperationEvidence;

/** Bounds and redacts operation labels before they cross the persistence boundary. */
public final class OperationTelemetrySanitizer {

    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(authorization|bearer|password|secret|token|cookie|api[._ /-]*key|private[._ /-]*key|vbg_[a-z0-9_-]+)");

    private OperationTelemetrySanitizer() {
    }

    public static String text(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("[^a-zA-Z0-9._ /-]", "_").trim();
        // Redact credential-like labels as a whole string; alternation must stay grouped.
        if (SENSITIVE.matcher(normalized).find()) return null;
        return normalized.substring(0, Math.min(maxLength, normalized.length()));
    }

    public static String type(String value) {
        String normalized = text(value, 24);
        if (normalized == null) return "OTHER";
        normalized = normalized.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ANALYZE", "MCP", "API", "IMPORT", "OTHER" -> normalized;
            default -> "OTHER";
        };
    }

    public static String status(String value) {
        String normalized = text(value, 24);
        if (normalized == null) return "FAILED";
        normalized = normalized.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SUCCESS", "FAILED", "STOPPED" -> normalized;
            default -> "FAILED";
        };
    }

    /** Sanitizes the complete public evidence record before it enters memory or any transport. */
    public static OperationEvidence evidence(OperationEvidence value) {
        if (value == null) return null;
        return new OperationEvidence(
                text(value.id(), 64),
                text(value.traceId(), 64),
                text(value.projectId(), 160),
                text(value.projectName(), 160),
                type(value.type()),
                text(value.operation(), 160),
                status(value.status()),
                value.startedAt(),
                value.completedAt(),
                nonNegative(value.durationMs()),
                Math.max(0, value.nodes()),
                Math.max(0, value.edges()),
                nonNegative(value.ramBeforeBytes()),
                nonNegative(value.ramPeakBytes()),
                nonNegative(value.ramIncreaseBytes()),
                nonNegative(value.ramAfterCooldownBytes()),
                value.cooldownComplete(),
                finite(value.cpuAvgPercent()),
                finite(value.cpuPeakPercent()),
                finite(value.cpuCoreSeconds()),
                nonNegative(value.storageAddedBytes()),
                nonNegative(value.diskReadBytes()),
                nonNegative(value.diskWriteBytes()),
                Math.max(0, value.concurrentHeavyOperations()),
                text(value.backendVersion(), 80),
                text(value.measurementType(), 32),
                text(value.confidence(), 16),
                text(value.stopReason(), 160));
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
