package com.vibegraph.watcher.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * File watcher properties (read from application.yaml).
 *
 * Example yaml:
 *
 * vibegraph:
 *   watcher:
 *     enabled: true
 *     debounce-ms: 500
 *     ignored-paths:
 *       - target
 *       - build
 *       - .git
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.watcher")
public class WatcherProperties {
    private boolean enabled = true;
    private long debounceMs = 500;
    private List<String> ignoredPaths = List.of("target", "build", ".git", ".idea", "node_modules");
    private List<String> watchedExtensions = List.of(".java");
}
