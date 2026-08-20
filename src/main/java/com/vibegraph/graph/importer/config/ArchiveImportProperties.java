package com.vibegraph.graph.importer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Archive import properties (read from application.yaml) for the archive-upload
 * onboarding flow (POST /api/projects/import-archive).
 *
 * Example yaml:
 *
 * vibegraph:
 *   import:
 *     archive:
 *       max-size: 200MB
 *       workspace-root: ${VIBEGRAPH_UPLOAD_WORKSPACE:${java.io.tmpdir}/vibegraph/uploads}
 *       ignored-paths:
 *         - target
 *         - build
 *         - .git
 *         - .idea
 *         - node_modules
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.import.archive")
public class ArchiveImportProperties {

    /**
     * Server hard limit per import request (upload size, download size, and extracted-byte
     * ceiling). Deliberately independent of the account quota: only the materialized .java
     * bytes count against the quota, and that check runs after extraction.
     */
    private DataSize maxSize = DataSize.ofMegabytes(200);

    /** Writable root where uploaded archives are materialized before parsing. Must never be the read-only /projects mount. */
    private Path workspaceRoot = Paths.get(System.getProperty("java.io.tmpdir"), "vibegraph", "uploads");

    /** Directory names skipped during extraction. */
    private List<String> ignoredPaths = List.of("target", "build", ".git", ".idea", "node_modules");
}
