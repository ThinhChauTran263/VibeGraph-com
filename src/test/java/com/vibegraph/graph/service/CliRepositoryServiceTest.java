package com.vibegraph.graph.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.dto.ApiKeyCreateRequest;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.dto.ProjectBindingResponse;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.ApiKeyService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;
import com.vibegraph.graph.dto.request.CliRepositoryCreateRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;

@ExtendWith(MockitoExtension.class)
@DisplayName("CLI repository service")
class CliRepositoryServiceTest {

    @TempDir
    Path workspaceRoot;

    @Mock ArchiveImportProperties archiveImportProperties;
    @Mock ProjectService projectService;
    @Mock ProjectOwnershipRegistrar ownershipRegistrar;
    @Mock ProjectUsageService projectUsageService;
    @Mock ApiKeyService apiKeyService;
    @Mock FeatureGateService featureGateService;
    @Mock AccountSettingsService accountSettingsService;
    @Mock CurrentUser currentUser;

    private CliRepositoryService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(archiveImportProperties.getWorkspaceRoot()).thenReturn(workspaceRoot);
        when(currentUser.id()).thenReturn(userId);
        service = new CliRepositoryService(
                archiveImportProperties,
                projectService,
                ownershipRegistrar,
                projectUsageService,
                apiKeyService,
                featureGateService,
                accountSettingsService,
                currentUser);
    }

    @Test
    @DisplayName("create allocates a server workspace and returns project-bound CLI commands")
    void createReturnsProjectKeyAndCommands() {
        AtomicReference<Path> sourceRef = new AtomicReference<>();
        when(projectService.createEmptyWorkspaceProject(eq("Demo"), any(Path.class))).thenAnswer(invocation -> {
            Path source = invocation.getArgument(1);
            sourceRef.set(source);
            return ProjectResponse.builder()
                    .id("cli123")
                    .name("Demo")
                    .rootPath(source.toString())
                    .status("CREATED")
                    .build();
        });
        when(apiKeyService.createForCurrentUser(any(ApiKeyCreateRequest.class))).thenReturn(
                new ApiKeyCreateResponse(
                        UUID.randomUUID(),
                        "vbg_abcd1234",
                        "Demo CLI",
                        "vbg_fullsecret",
                        new ProjectBindingResponse("cli123", "Demo", "LOCAL", "ANALYZING"),
                        Instant.now(),
                        null));

        var response = service.create(new CliRepositoryCreateRequest("Demo"));

        assertThat(sourceRef.get()).startsWith(workspaceRoot.resolve("cli"));
        assertThat(Files.isDirectory(sourceRef.get())).isTrue();
        assertThat(response.project().getId()).isEqualTo("cli123");
        assertThat(response.apiKey().secretKey()).isEqualTo("vbg_fullsecret");
        assertThat(response.commands()).containsExactly(
                "vibegraph login",
                "vibegraph push",
                "vibegraph watch");
        assertThat(response.commands()).noneMatch(command -> command.contains("vbg_fullsecret"));
        verify(featureGateService).assertEnabled(FeatureGateService.CLI_PUSH);
        verify(accountSettingsService).assertNotBlocked(userId);
        verify(ownershipRegistrar).registerLocal("cli123", "Demo");
        verify(projectUsageService).recordImport("cli123", userId, 0L);
    }

    @Test
    @DisplayName("create cleans the workspace and ownership if API key creation fails")
    void createCleansUpWhenApiKeyCreationFails() {
        AtomicReference<Path> sourceRef = new AtomicReference<>();
        when(projectService.createEmptyWorkspaceProject(eq("Demo"), any(Path.class))).thenAnswer(invocation -> {
            Path source = invocation.getArgument(1);
            sourceRef.set(source);
            return ProjectResponse.builder()
                    .id("cli123")
                    .name("Demo")
                    .rootPath(source.toString())
                    .status("CREATED")
                    .build();
        });
        doThrow(new IllegalStateException("limit reached"))
                .when(apiKeyService).createForCurrentUser(any(ApiKeyCreateRequest.class));

        assertThatThrownBy(() -> service.create(new CliRepositoryCreateRequest("Demo")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("limit reached");

        verify(projectService).deleteProject("cli123");
        verify(ownershipRegistrar).unregister("cli123");
        assertThat(Files.exists(sourceRef.get().getParent())).isFalse();
    }
}
