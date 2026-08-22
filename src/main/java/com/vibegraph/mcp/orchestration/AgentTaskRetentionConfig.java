package com.vibegraph.mcp.orchestration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers task retention properties. */
@Configuration
@EnableConfigurationProperties(AgentTaskRetentionProperties.class)
public class AgentTaskRetentionConfig {
}
