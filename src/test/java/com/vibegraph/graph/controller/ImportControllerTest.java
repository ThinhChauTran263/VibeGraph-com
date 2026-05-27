package com.vibegraph.graph.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ImportController - GitHub tarball import endpoint.
 *
 * Setup with @WebMvcTest(ImportController.class) + @MockBean TarballImportService.
 *
 * Run: mvn test -Dtest=ImportControllerTest
 */
@DisplayName("ImportController")
class ImportControllerTest {

    @Nested
    @DisplayName("POST /api/projects/import-github")
    class ImportGithub {

        @Test
        @Disabled("Chờ ImportController + MockMvc + TarballImportService mock")
        @DisplayName("should return 202 Accepted with projectId on valid URL")
        void shouldReturn202OnValidUrl() {
            // mockMvc.perform(post("/api/projects/import-github")
            //     .contentType(MediaType.APPLICATION_JSON)
            //     .content("{\"url\":\"https://github.com/spring-projects/spring-petclinic\"}"))
            //     .andExpect(status().isAccepted())
            //     .andExpect(jsonPath("$.success").value(true))
            //     .andExpect(jsonPath("$.data.id").exists());
        }

        @Test
        @Disabled("Chờ ImportController + MockMvc setup")
        @DisplayName("should return 400 for malformed URL")
        void shouldReturn400ForInvalidUrl() {
            // mockMvc.perform(post("/api/projects/import-github")
            //     .contentType(MediaType.APPLICATION_JSON)
            //     .content("{\"url\":\"not-a-github-url\"}"))
            //     .andExpect(status().isBadRequest());
        }

        @Test
        @Disabled("Chờ ImportController + MockMvc setup")
        @DisplayName("should return 400 for blank URL")
        void shouldReturn400ForBlankUrl() {
            // mockMvc.perform(post("/api/projects/import-github")
            //     .contentType(MediaType.APPLICATION_JSON)
            //     .content("{\"url\":\"\"}"))
            //     .andExpect(status().isBadRequest());
        }

        @Test
        @Disabled("Chờ GlobalExceptionHandler maps GithubImportException → 400")
        @DisplayName("should return 400 when private repo (GithubImportException)")
        void shouldReturn400ForPrivateRepo() {
            // when(tarballImportService.importFromGithub(any()))
            //     .thenThrow(new GithubImportException("Repo is private"));
            // mockMvc.perform(post("/api/projects/import-github")
            //     .contentType(MediaType.APPLICATION_JSON)
            //     .content("{\"url\":\"https://github.com/owner/private-repo\"}"))
            //     .andExpect(status().isBadRequest());
        }

        @Test
        @Disabled("Chờ GlobalExceptionHandler maps GithubImportException → 400")
        @DisplayName("should return 400 when repo size exceeds 100MB")
        void shouldReturn400ForOversizedRepo() {
            // when(tarballImportService.importFromGithub(any()))
            //     .thenThrow(new GithubImportException("Repo size exceeds 100MB limit"));
        }
    }
}
