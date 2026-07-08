package com.vibegraph.graph.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for TarballImportService - streams GitHub tarball into parser.
 *
 * Pipeline tested:
 *   1. Pre-flight check (GitHub API metadata)
 *   2. Tarball stream + parse in-memory
 *   3. Persist nodes/edges via GraphRepository
 *   4. WebSocket progress notifications
 *
 * Run: mvn test -Dtest=TarballImportServiceTest
 */
@DisplayName("TarballImportService")
class TarballImportServiceTest {

    @Nested
    @DisplayName("Pre-flight validation")
    class PreFlight {

        @Test
        @Disabled("Chờ TarballImportServiceImpl + WireMock GitHub API mock")
        @DisplayName("should reject private repository")
        void shouldRejectPrivateRepo() {
            // wireMock.stubFor(get("/repos/owner/private")
            //     .willReturn(jsonResponse("{\"private\": true, \"size\": 1024}")));
            // assertThatThrownBy(() -> service.importFromGithub(new GithubImportRequest(...)))
            //     .isInstanceOf(GithubImportException.class)
            //     .hasMessageContaining("private");
        }

        @Test
        @Disabled("Chờ TarballImportServiceImpl + WireMock setup")
        @DisplayName("should reject repo larger than 100MB")
        void shouldRejectOversizedRepo() {
            // GitHub API returns size in KB, 100MB = 102400 KB
            // wireMock.stubFor(get(...).willReturn(jsonResponse("{\"size\": 200000}")));
            // assertThatThrownBy(...).hasMessageContaining("size");
        }

        @Test
        @Disabled("Chờ TarballImportServiceImpl + WireMock setup")
        @DisplayName("should reject non-existent repository (404)")
        void shouldRejectNonExistentRepo() {
            // wireMock.stubFor(get(...).willReturn(notFound()));
            // assertThatThrownBy(...).isInstanceOf(GithubImportException.class);
        }
    }

    @Nested
    @DisplayName("Tarball streaming")
    class TarballStream {

        @Test
        @Disabled("Chờ TarballImportServiceImpl + sample tarball fixture")
        @DisplayName("should stream tarball without writing to disk")
        void shouldStreamWithoutDiskWrite() {
            // Verify no temp files created during import
        }

        @Test
        @Disabled("Chờ TarballImportServiceImpl + ParserService mock")
        @DisplayName("should filter and parse only .java files")
        void shouldParseOnlyJavaFiles() {
            // Mock tarball with .java + .md + .png files
            // Verify ParserService.parseString called only for .java entries
        }

        @Test
        @Disabled("Chờ TarballImportServiceImpl")
        @DisplayName("should skip build/target/.git directories")
        void shouldSkipExcludedDirs() {
        }
    }

    @Nested
    @DisplayName("Async parsing")
    class AsyncParsing {

        @Test
        @Disabled("Chờ TarballImportServiceImpl + @Async config")
        @DisplayName("should return projectId immediately (status=ANALYZING)")
        void shouldReturnImmediatelyWithAnalyzingStatus() {
            // Pre-flight runs sync, parsing runs async
            // ProjectResponse response = service.importFromGithub(request);
            // assertThat(response.getStatus()).isEqualTo("ANALYZING");
        }

        @Test
        @Disabled("Chờ TarballImportServiceImpl + SimpMessagingTemplate mock")
        @DisplayName("should push progress via WebSocket /topic/projects/{id}/status")
        void shouldPushProgressViaWebSocket() {
            // verify(simpMessagingTemplate).convertAndSend(
            //     eq("/topic/projects/" + projectId + "/status"),
            //     argThat(progress -> progress.getProgress() > 0)
            // );
        }
    }
}
