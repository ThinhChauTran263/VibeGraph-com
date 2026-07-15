package com.vibegraph.auth.service;

import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.graph.config.ProjectsProperties;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminStorageService")
class AdminStorageServiceTest {

    @TempDir java.nio.file.Path tempDir;
    @Mock private ProjectUsageRepository projectUsageRepository;

    @Test
    @DisplayName("overview reports configured mount metrics and unknown DB capacities")
    void overview_configuredMounts_reportsUnknownDatabaseCapacity() {
        ProjectsProperties projectsProperties = new ProjectsProperties();
        projectsProperties.setAllowedRoot(tempDir.toString());
        ArchiveImportProperties archiveProperties = new ArchiveImportProperties();
        archiveProperties.setWorkspaceRoot(Paths.get(System.getProperty("java.io.tmpdir")));
        when(projectUsageRepository.sumStorageBytes()).thenReturn(123L);
        AdminStorageService service = new AdminStorageService(projectUsageRepository, projectsProperties, archiveProperties);

        var response = service.overview();

        assertEquals(123L, response.trackedProjectUsageBytes());
        assertEquals("UNKNOWN", response.database().status());
        assertEquals("UNKNOWN", response.neo4j().status());
        assertTrue(response.mounts().stream().anyMatch(m -> m.label().equals("projects") && m.available()));
        assertTrue(response.mounts().stream().allMatch(m -> m.path() == null));
    }
}
