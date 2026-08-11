package com.vibegraph.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProductionPolicyTest {

    @Test
    void defaults_useThirtyMinuteAccessAndSevenDayRefreshLifetimes() {
        JwtProperties properties = new JwtProperties();

        assertThat(properties.getExpirationMs()).isEqualTo(1_800_000L);
        assertThat(properties.getRefreshExpirationMs()).isEqualTo(604_800_000L);
    }
}
