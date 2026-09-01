package com.vibegraph.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.OperationEvidence;
import com.vibegraph.infrastructure.persistence.entity.InfrastructureOperationHistory;

class InfrastructureSnapshotSafetyTest {

    @Test
    void persistedEvidenceDoesNotExposeSecretLikeStopReasons() throws Exception {
        OperationEvidence evidence = new OperationEvidence(
                "evt-safe", "trace-safe", "project-safe", "Demo", "MCP", "inspect", "STOPPED",
                Instant.parse("2026-08-25T10:00:00Z"), Instant.parse("2026-08-25T10:00:01Z"), 1000,
                0, 0, 1, 2, 1, 2, true, 1, 2, 0.1, 0, 0, 0, 0, "runtime", "OBSERVED", "LOW",
                "Authorization: Bearer do-not-store");

        OperationEvidence safe = InfrastructureOperationHistory.from(evidence).toEvidence();

        assertThat(safe.stopReason()).isNull();
        assertThat(safe.operation()).doesNotContain("Bearer").doesNotContain("Authorization");
    }

    @Test
    void evidenceFieldsRemainBoundedAtThePersistenceBoundary() {
        String longName = "x".repeat(500);
        OperationEvidence evidence = new OperationEvidence(
                "evt-bound", "trace-bound", "project", longName, "UNKNOWN", "operation", "UNKNOWN",
                Instant.EPOCH, Instant.EPOCH, 0, 0, 0, 0, 0, 0, 0, true, 0, 0, 0, 0, 0, 0, 0,
                "runtime", "OBSERVED", "LOW", null);

        InfrastructureOperationHistory row = InfrastructureOperationHistory.from(evidence);

        assertThat(row.getProjectName()).hasSize(160);
        assertThat(row.getType()).isEqualTo("OTHER");
        assertThat(row.getStatus()).isEqualTo("FAILED");
    }

    @ParameterizedTest
    @MethodSource("sensitiveLabels")
    void sensitiveLabelsAreRemovedBeforePersistence(String label) {
        OperationEvidence evidence = new OperationEvidence(
                "evt-sensitive-" + Math.abs(label.hashCode()), "trace", "project", "Demo", "API", label,
                "FAILED", Instant.EPOCH, Instant.EPOCH, 0, 0, 0, 0, 0, 0, 0, true,
                0, 0, 0, 0, 0, 0, 0, "runtime", "OBSERVED", "LOW", label);

        InfrastructureOperationHistory row = InfrastructureOperationHistory.from(evidence);

        assertThat(row.getOperation()).isNull();
        assertThat(row.getStopReason()).isNull();
    }

    static Stream<Arguments> sensitiveLabels() {
        return Stream.of(
                Arguments.of("Authorization: Bearer do-not-store"),
                Arguments.of("vbg_demo_secret"),
                Arguments.of("cookie=session"),
                Arguments.of("private-key material"),
                Arguments.of("password=hidden"));
    }
}
