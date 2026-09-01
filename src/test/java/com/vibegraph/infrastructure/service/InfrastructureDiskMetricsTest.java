package com.vibegraph.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.graph.config.GraphPayloadProperties;
import com.vibegraph.infrastructure.config.InfrastructureMonitorProperties;
import com.vibegraph.infrastructure.service.collector.CAdvisorContainerCollector;
import com.vibegraph.mcp.config.McpLimitProperties;

class InfrastructureDiskMetricsTest {

    @Test
    void projectUsageFailureIsUnavailableInsteadOfMeasuredZero() {
        InfrastructureMonitorProperties properties = new InfrastructureMonitorProperties();
        properties.setCAdvisorEnabled(false);
        ProjectUsageRepository usageRepository = mock(ProjectUsageRepository.class);
        when(usageRepository.sumStorageBytes()).thenThrow(new IllegalStateException("database unavailable"));
        @SuppressWarnings("unchecked")
        ObjectProvider<OperationTelemetryRecorder> recorderProvider = mock(ObjectProvider.class);
        InfrastructureMetricsService service = new InfrastructureMetricsService(
                properties,
                usageRepository,
                new GraphPayloadProperties(),
                new McpLimitProperties(),
                mock(InfrastructureEventStream.class),
                recorderProvider,
                Clock.systemUTC(),
                mock(CAdvisorContainerCollector.class));

        var snapshot = service.snapshot();
        var tracked = snapshot.disk().breakdown().stream()
                .filter(row -> "tracked-projects".equals(row.key()))
                .findFirst()
                .orElseThrow();

        assertThat(snapshot.disk().status()).isEqualTo("MEASURED");
        assertThat(tracked.usedBytes()).isZero();
        assertThat(tracked.status()).isEqualTo("UNAVAILABLE");
    }
}
