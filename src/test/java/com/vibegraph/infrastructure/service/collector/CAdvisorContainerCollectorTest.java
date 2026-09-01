package com.vibegraph.infrastructure.service.collector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.infrastructure.config.InfrastructureMonitorProperties;
import org.junit.jupiter.api.Test;

class CAdvisorContainerCollectorTest {

    @Test
    void parsesLatestContainerSampleWithoutClaimingDockerHealth() {
        InfrastructureMonitorProperties properties = new InfrastructureMonitorProperties();
        CAdvisorContainerCollector collector = new CAdvisorContainerCollector(properties, new ObjectMapper());

        var values = collector.parse(("""
                [{
                  "name":"/docker/abc",
                  "aliases":["/backend"],
                  "spec":{"creation_time":"2026-08-25T10:00:00Z"},
                  "stats":[{
                    "timestamp":"2026-08-25T10:00:02Z",
                    "memory":{"usage":123456,"working_set":100000},
                    "cpu":{"usage":{"total":2000000000}}
                  }]
                }, {
                  "name":"/",
                  "stats":[]
                }]
                """).getBytes());

        assertThat(values).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("backend");
            assertThat(item.memoryUsedBytes()).isEqualTo(100000);
            assertThat(item.healthKnown()).isFalse();
            assertThat(item.healthStatus()).isEqualTo("UNAVAILABLE");
            assertThat(item.uptimeSeconds()).isEqualTo(2);
        });
    }

    @Test
    void rejectsMalformedOrOversizedShapeAsUnavailableResult() {
        InfrastructureMonitorProperties properties = new InfrastructureMonitorProperties();
        CAdvisorContainerCollector collector = new CAdvisorContainerCollector(properties, new ObjectMapper());

        assertThat(collector.parse("not-json".getBytes())).isEmpty();
        assertThat(collector.parse("{\"containers\":{}}".getBytes())).isEmpty();
    }
}
