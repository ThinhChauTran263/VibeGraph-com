package com.vibegraph.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.vibegraph.common.exception.ServiceBusyException;

class OperationTelemetryAdmissionTest {

    @Test
    void requireAccepted_allowsAcceptedOrDisabledTelemetry() {
        assertThatCode(() -> OperationTelemetryRecorder.requireAccepted(null)).doesNotThrowAnyException();
        assertThatCode(() -> OperationTelemetryRecorder.requireAccepted(
                new OperationTelemetryRecorder.OperationToken("evt-ok"))).doesNotThrowAnyException();
    }

    @Test
    void requireAccepted_rejectsTerminalAdmissionToken() {
        assertThatThrownBy(() -> OperationTelemetryRecorder.requireAccepted(
                OperationTelemetryRecorder.OperationToken.rejected("evt-busy")))
                .isInstanceOf(ServiceBusyException.class)
                .hasMessageContaining("busy");
    }
}
