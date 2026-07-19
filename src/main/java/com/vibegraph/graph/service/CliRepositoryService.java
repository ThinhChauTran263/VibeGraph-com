package com.vibegraph.graph.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.dto.ApiKeyCreateRequest;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.ApiKeyService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;
import com.vibegraph.graph.dto.request.CliRepositoryCreateRequest;
import com.vibegraph.graph.dto.response.CliRepositorySetupResponse;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates a server-owned empty workspace for the CLI push flow.
 *
 * <p>The browser never sends a local absolute path. It only receives a project-bound
 * one-time API key and commands that push relative paths from the user's machine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CliRepositoryService {

    private final ArchiveImportProperties archiveImportProperties;
    private final ProjectService projectService;
    private final ProjectOwnershipRegistrar ownershipRegistrar;
    private final ProjectUsageService projectUsageService;
    private final ApiKeyService apiKeyService;
    private final FeatureGateService featureGateService;
    private final AccountSettingsService accountSettingsService;
    private final CurrentUser currentUser;

    @Transactional
    public CliRepositorySetupResponse create(CliRepositoryCreateRequest request) {
        featureGateService.assertEnabled(FeatureGateService.CLI_PUSH);
        UUID userId = currentUser.id();
        accountSettingsService.assertNotBlocked(userId);

        Path workspace = archiveImportProperties.getWorkspaceRoot()
                .resolve("cli")
                .resolve(UUID.randomUUID().toString());
        Path source = workspace.resolve("source");
        String createdProjectId = null;
        try {
            Files.createDirectories(source);
            ProjectResponse project = projectService.createEmptyWorkspaceProject(
                    request == null ? null : request.name(), source);
            createdProjectId = project.getId();
            ownershipRegistrar.registerLocal(project.getId(), project.getName());
            projectUsageService.recordImport(project.getId(), userId, 0L);

            ApiKeyCreateResponse apiKey = apiKeyService.createForCurrentUser(
                    new ApiKeyCreateRequest(defaultKeyName(project.getName()), project.getId()));
            return new CliRepositorySetupResponse(project, apiKey, commands(apiKey.secretKey()));
        } catch (IOException ex) {
            cleanup(workspace, createdProjectId);
            throw new IllegalStateException("Could not create CLI repository workspace", ex);
        } catch (RuntimeException ex) {
            cleanup(workspace, createdProjectId);
            throw ex;
        }
    }

    private String defaultKeyName(String projectName) {
        String normalized = projectName == null || projectName.isBlank() ? "Repository" : projectName.trim();
        return normalized.length() > 100
                ? normalized.substring(0, 100) + " CLI"
                : normalized + " CLI";
    }

    private List<String> commands(String secretKey) {
        return List.of(
                "vibegraph login " + secretKey,
                "vibegraph push",
                "vibegraph watch");
    }

    private void cleanup(Path workspace, String projectId) {
        if (projectId != null) {
            try {
                projectService.deleteProject(projectId);
            } catch (RuntimeException ex) {
                log.warn("Failed to clean CLI workspace project {}: {}", projectId, ex.getMessage());
            }
            try {
                ownershipRegistrar.unregister(projectId);
            } catch (RuntimeException ex) {
                log.warn("Failed to clean CLI workspace ownership {}: {}", projectId, ex.getMessage());
            }
        }
        deleteRecursively(workspace);
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ex) {
                    log.warn("Failed to delete {}: {}", candidate, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("Failed to clean CLI workspace {}: {}", path, ex.getMessage());
        }
    }
}
