package com.vibegraph.auth.cli;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Runtime settings for the CLI browser authorization flow. */
@Getter
@Setter
@ConfigurationProperties(prefix = "vibegraph.cli.device")
public class CliDeviceAuthorizationProperties {

    private String frontendUrl = "http://localhost:5173";
    private long ttlSeconds = 600;
    private int pollIntervalSeconds = 2;

    public CliDeviceAuthorizationProperties() {
    }

    public CliDeviceAuthorizationProperties(String frontendUrl, long ttlSeconds, int pollIntervalSeconds) {
        this.frontendUrl = frontendUrl;
        this.ttlSeconds = ttlSeconds;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }
}
