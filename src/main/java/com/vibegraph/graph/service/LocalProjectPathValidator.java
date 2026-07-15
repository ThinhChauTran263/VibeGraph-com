package com.vibegraph.graph.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.vibegraph.graph.config.ProjectsProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalProjectPathValidator {

    private final ProjectsProperties properties;

    public Path validateImportRoot(String rawRootPath) {
        if (rawRootPath == null || rawRootPath.isBlank()) {
            throw new IllegalArgumentException("rootPath is required");
        }
        Path allowedRoot = resolveAllowedRoot();
        if (allowedRoot == null && !properties.isAllowUnconfinedImport()) {
            throw new IllegalStateException(
                    "Local import is disabled until an allowed root is configured");
        }
        Path root = requireRealDirectory(rawRootPath, "rootPath must be an existing directory");
        if (allowedRoot != null && !root.startsWith(allowedRoot)) {
            throw new IllegalArgumentException("rootPath must be inside the configured allowed root");
        }
        return root;
    }

    public Path resolveAllowedRoot() {
        String configuredRoot = properties.getAllowedRoot();
        if (configuredRoot == null || configuredRoot.isBlank()) {
            return null;
        }
        return requireRealDirectory(
                configuredRoot, "Configured allowed-root is not accessible");
    }

    private Path requireRealDirectory(String rawPath, String message) {
        try {
            Path path = Path.of(rawPath).toRealPath();
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException(message);
            }
            return path;
        } catch (InvalidPathException | IOException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }
}
