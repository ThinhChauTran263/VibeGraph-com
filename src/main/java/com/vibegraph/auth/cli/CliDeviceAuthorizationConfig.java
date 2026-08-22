package com.vibegraph.auth.cli;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers CLI authorization settings without changing the main security chain. */
@Configuration
@EnableConfigurationProperties(CliDeviceAuthorizationProperties.class)
public class CliDeviceAuthorizationConfig {
}
