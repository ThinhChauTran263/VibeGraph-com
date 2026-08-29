package com.vibegraph.abuse;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AbuseConfiguration {

    @Bean
    public ClientAddressResolver clientAddressResolver(AbuseProperties abuseProperties) {
        return new ClientAddressResolver(abuseProperties);
    }

    @Bean
    public Clock abuseClock() {
        return Clock.systemUTC();
    }
}
