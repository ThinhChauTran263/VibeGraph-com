package com.vibegraph.auth.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.dto.AdminStorageOverviewResponse;
import com.vibegraph.auth.dto.StorageMountResponse;
import com.vibegraph.auth.dto.StorageUnknownResponse;
import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.graph.config.ProjectsProperties;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStorageService {

    private final ProjectUsageRepository projectUsageRepository;
    private final ProjectsProperties projectsProperties;
    private final ArchiveImportProperties archiveImportProperties;

    @Transactional(readOnly = true)
    public AdminStorageOverviewResponse overview() {
        List<StorageMountResponse> mounts = new ArrayList<>();
        if (projectsProperties.getAllowedRoot() == null || projectsProperties.getAllowedRoot().isBlank()) {
            mounts.add(unavailable("projects", "vibegraph.projects.allowed-root", "UNCONFIGURED"));
        } else {
            mounts.add(readMount("projects", "vibegraph.projects.allowed-root", Path.of(projectsProperties.getAllowedRoot())));
        }
        mounts.add(readMount("uploads", "vibegraph.import.archive.workspace-root", archiveImportProperties.getWorkspaceRoot()));
        return new AdminStorageOverviewResponse(
                projectUsageRepository.sumStorageBytes(),
                mounts,
                new StorageUnknownResponse("database", "UNKNOWN", "Database storage capacity is not safely measurable from this service"),
                new StorageUnknownResponse("neo4j", "UNKNOWN", "Neo4j storage capacity is not safely measurable from this service"));
    }

    private StorageMountResponse readMount(String label, String source, Path path) {
        try {
            Path absolute = path.toAbsolutePath().normalize();
            if (!Files.exists(absolute)) {
                return new StorageMountResponse(label, source, null, false, null, null, null, "MISSING");
            }
            var store = Files.getFileStore(absolute);
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            return new StorageMountResponse(label, source, null, true, total, usable, total - usable, "OK");
        } catch (IOException | SecurityException ex) {
            return new StorageMountResponse(label, source, null, false, null, null, null, "UNREADABLE");
        }
    }

    private StorageMountResponse unavailable(String label, String source, String status) {
        return new StorageMountResponse(label, source, null, false, null, null, null, status);
    }
}
