package com.vibegraph.abuse;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "vibegraph.abuse")
public class AbuseProperties {

    private int concurrentImportsPerUser = 1;
    private int requestsPerMinutePerIp = 120;
    private int requestsPerMinutePerUser = 240;
    private int requestsPerMinutePerApiKey = 240;
    private boolean trustProxy = false;
    private List<String> trustedProxies = List.of();
}
