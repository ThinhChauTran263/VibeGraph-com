package com.vibegraph.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InfrastructureMonitorProperties.class)
public class InfrastructureMonitorConfig {
}
