package com.vibegraph.graph.controller;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import com.vibegraph.common.exception.ArchiveImportException;
import com.vibegraph.common.exception.ArchiveImportException.Reason;
import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.ProjectRefreshInProgressException;
import com.vibegraph.common.exception.RepositoryUpToDateException;
import com.vibegraph.graph.dto.request.GithubImportRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.ArchiveImportService;
import com.vibegraph.graph.service.TarballImportService;

/**
 * Web-layer tests for ImportController using standalone MockMvc - no Neo4j, no full context.
 *
 * Run: mvn test -Dtest=ImportControllerTest
 */
@DisplayName("ImportController")
class ImportControllerTest {

    private MockMvc mockMvc;
    private ArchiveImportService archiveImportService;
    private TarballImportService tarballImportService;

    @BeforeEach
    void setUp() {
        archiveImportService = Mockito.mock(ArchiveImportService.class);
        tarballImportService = Mockito.mock(TarballImportService.class);
        ImportController controller = new ImportController(tarballImportService, archiveImportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/projects/import-archive returns 200 with the project and forwards name + file")
    void shouldImportArchive() throws Exception {
        ProjectResponse project = ProjectResponse.builder()
                .id("p1").name("demo").status("ANALYZED").totalFiles(1).build();
        when(archiveImportService.importArchive(eq("demo"), any(MultipartFile.class))).thenReturn(project);
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", "data".getBytes());

        mockMvc.perform(multipart("/api/projects/import-archive").file(file).param("name", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("p1"))
                .andExpect(jsonPath("$.data.name").value("demo"));

        ArgumentCaptor<MultipartFile> captor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(archiveImportService).importArchive(eq("demo"), captor.capture());
        assertThat(captor.getValue().getOriginalFilename()).isEqualTo("project.zip");
        verify(archiveImportService, never()).importArchiveAsync(any(), any());
    }

    @Test
    @DisplayName("POST /api/projects/import-archive?async=true returns 202 and calls the async service")
    void shouldImportArchiveAsync() throws Exception {
        ProjectResponse project = ProjectResponse.builder()
                .id("p2").name("demo").status("ANALYZING").progress(0).build();
        when(archiveImportService.importArchiveAsync(eq("demo"), any(MultipartFile.class))).thenReturn(project);
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", "data".getBytes());

        mockMvc.perform(multipart("/api/projects/import-archive")
                        .file(file).param("name", "demo").param("async", "true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("p2"))
                .andExpect(jsonPath("$.data.status").value("ANALYZING"))
                .andExpect(jsonPath("$.data.progress").value(0));

        verify(archiveImportService).importArchiveAsync(eq("demo"), any(MultipartFile.class));
        verify(archiveImportService, never()).importArchive(any(), any());
    }

    @Test
    @DisplayName("POST /api/projects/import-github returns 202 with ANALYZING project")
    void shouldImportGithubRepository() throws Exception {
        ProjectResponse project = ProjectResponse.builder()
                .id("p3").name("acme/demo").status("ANALYZING").progress(0).build();
        when(tarballImportService.importFromGithub(new GithubImportRequest("https://github.com/acme/demo")))
                .thenReturn(project);

        mockMvc.perform(post("/api/projects/import-github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/acme/demo\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("p3"))
                .andExpect(jsonPath("$.data.name").value("acme/demo"))
                .andExpect(jsonPath("$.data.status").value("ANALYZING"));

        verify(tarballImportService).importFromGithub(new GithubImportRequest("https://github.com/acme/demo"));
    }

    @Test
    @DisplayName("POST /api/projects/import-github rejects invalid GitHub URL before import")
    void shouldRejectInvalidGithubUrlBeforeImport() throws Exception {
        mockMvc.perform(post("/api/projects/import-github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/acme/demo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(tarballImportService, never()).importFromGithub(any());
    }

    @Test
    @DisplayName("GitHub import failures map to 422 with safe error envelope")
    void shouldMapGithubImportExceptionTo422() throws Exception {
        when(tarballImportService.importFromGithub(new GithubImportRequest("https://github.com/acme/private")))
                .thenThrow(new GithubImportException("GitHub repository is private or not found"));

        mockMvc.perform(post("/api/projects/import-github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/acme/private\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GITHUB_IMPORT_ERROR"))
                .andExpect(jsonPath("$.error.message").value("GitHub repository is private or not found"));

        verify(tarballImportService).importFromGithub(new GithubImportRequest("https://github.com/acme/private"));
    }

    @Test
    @DisplayName("GitHub re-import of an up-to-date repository maps to 409 REPO_UP_TO_DATE")
    void shouldMapRepositoryUpToDateTo409() throws Exception {
        when(tarballImportService.importFromGithub(new GithubImportRequest("https://github.com/acme/demo")))
                .thenThrow(new RepositoryUpToDateException("acme/demo"));

        mockMvc.perform(post("/api/projects/import-github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/acme/demo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REPO_UP_TO_DATE"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("up to date")));
    }

    @Test
    @DisplayName("GitHub re-import while still analyzing maps to 409 REFRESH_IN_PROGRESS")
    void shouldMapRefreshInProgressTo409() throws Exception {
        when(tarballImportService.importFromGithub(new GithubImportRequest("https://github.com/acme/demo")))
                .thenThrow(new ProjectRefreshInProgressException("acme/demo"));

        mockMvc.perform(post("/api/projects/import-github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/acme/demo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REFRESH_IN_PROGRESS"));
    }

    @Test
    @DisplayName("ArchiveImportException is mapped to 400 with an ARCHIVE_* error code")
    void shouldMapArchiveImportExceptionTo400() throws Exception {
        when(archiveImportService.importArchive(eq("demo"), any(MultipartFile.class)))
                .thenThrow(new ArchiveImportException(Reason.UNSUPPORTED_TYPE, "bad type"));
        MockMultipartFile file = new MockMultipartFile("file", "evil.rar", "application/octet-stream", "x".getBytes());

        mockMvc.perform(multipart("/api/projects/import-archive").file(file).param("name", "demo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ARCHIVE_UNSUPPORTED_TYPE"));

    }
}
