package com.vibegraph.abuse;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "vibegraph.abuse")
@Validated
public class AbuseProperties {

    @Min(0)
    private int concurrentImportsPerUser = 1;
    @Min(0)
    private int requestsPerMinutePerIp = 120;
    @Min(0)
    private int requestsPerMinutePerUser = 240;
    @Min(0)
    private int requestsPerMinutePerApiKey = 240;
    private boolean trustProxy = false;
    private List<String> trustedProxies = List.of();
}
