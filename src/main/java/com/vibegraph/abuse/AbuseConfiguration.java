package com.vibegraph.abuse;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AbuseConfiguration {

    @Bean
    public Clock abuseClock() {
        return Clock.systemUTC();
    }
}
