package com.vibegraph.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
