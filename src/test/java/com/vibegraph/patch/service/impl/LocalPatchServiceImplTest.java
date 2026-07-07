package com.vibegraph.patch.service.impl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.patch.config.LocalPatchProperties;
import com.vibegraph.patch.dto.request.PatchRequest;
import com.vibegraph.patch.dto.request.PatchRequest.PatchDeletion;
import com.vibegraph.patch.dto.request.PatchRequest.PatchFileChange;
import com.vibegraph.patch.dto.response.PatchResult;
import com.vibegraph.patch.exception.PatchRejectedException;
import com.vibegraph.patch.exception.PatchRejectedException.Reason;

/**
 * Unit tests for {@link LocalPatchServiceImpl} over a real temp project root. The project root is
 * supplied via a mocked {@link SourceFileService#resolveProjectRoot(String)}; every other rule is
 * exercised directly against the filesystem.
 *
 * Run: mvn test -Dtest=LocalPatchServiceImplTest
 */
@DisplayName("LocalPatchServiceImpl")
class LocalPatchServiceImplTest {

    private static final String PROJECT_ID = "p1";

    @TempDir
    Path root;

    private SourceFileService sourceFileService;
    private LocalPatchServiceImpl service;

    @BeforeEach
    void setUp() {
        sourceFileService = Mockito.mock(SourceFileService.class);
        when(sourceFileService.resolveProjectRoot(PROJECT_ID)).thenReturn(root);
        service = new LocalPatchServiceImpl(sourceFileService, new LocalPatchProperties());
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private PatchRequest changeOf(String path, String content, boolean dryRun) {
        return new PatchRequest(
                List.of(new PatchFileChange(path, b64(content), "base64")),
                List.of(),
                dryRun);
    }

    // --- happy paths ---------------------------------------------------------------------------

    @Test
    @DisplayName("writes a changed file under the root and flags requiresAnalyze when not a dry run")
    void writesChangedFile() throws Exception {
        PatchResult result = service.applyPatch(PROJECT_ID,
                changeOf("src/main/java/com/example/App.java", "class App {}", false));

        Path written = root.resolve("src/main/java/com/example/App.java");
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.readString(written)).isEqualTo("class App {}");
        assertThat(result.changed()).isEqualTo(1);
        assertThat(result.deleted()).isZero();
        assertThat(result.requiresAnalyze()).isTrue();
    }

    @Test
    @DisplayName("removes an existing file for a valid deletion")
    void removesDeletedFile() throws Exception {
        Path existing = root.resolve("src/Old.java");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "old");

        PatchResult result = service.applyPatch(PROJECT_ID,
                new PatchRequest(List.of(), List.of(new PatchDeletion("src/Old.java")), false));

        assertThat(Files.exists(existing)).isFalse();
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.requiresAnalyze()).isTrue();
    }

    @Test
    @DisplayName("dryRun validates but never writes or deletes")
    void dryRunDoesNotMutate() throws Exception {
        Path toDelete = root.resolve("src/Old.java");
        Files.createDirectories(toDelete.getParent());
        Files.writeString(toDelete, "old");

        PatchRequest request = new PatchRequest(
                List.of(new PatchFileChange("src/New.java", b64("class New {}"), "base64")),
                List.of(new PatchDeletion("src/Old.java")),
                true);

        PatchResult result = service.applyPatch(PROJECT_ID, request);

        assertThat(Files.exists(root.resolve("src/New.java"))).isFalse();
        assertThat(Files.exists(toDelete)).isTrue();
        assertThat(result.changed()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.requiresAnalyze()).isFalse();
    }

    // --- rejections ----------------------------------------------------------------------------

    @Test
    @DisplayName("rejects a path traversal attempt")
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf("../../etc/passwd", "x", false)))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.PATH_TRAVERSAL);
    }

    @Test
    @DisplayName("rejects an absolute POSIX path")
    void rejectsAbsolutePath() {
        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf("/etc/passwd", "x", false)))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.ABSOLUTE_PATH);
    }

    @Test
    @DisplayName("rejects a Windows drive path")
    void rejectsWindowsDrivePath() {
        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf("C:\\Windows\\system32\\evil.dll", "x", false)))
                .isInstanceOf(PatchRejectedException.class);
    }

    @Test
    @DisplayName("rejects a .env file")
    void rejectsDotEnv() {
        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf(".env", "SECRET=1", false)))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.BLOCKED_FILE);
    }

    @Test
    @DisplayName("rejects a private key file")
    void rejectsPrivateKey() {
        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf("secrets/server.key", "key", false)))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.BLOCKED_FILE);
    }

    @Test
    @DisplayName("rejects binary content")
    void rejectsBinaryContent() {
        String binaryB64 = Base64.getEncoder().encodeToString(new byte[] {1, 2, 0, 3});
        PatchRequest request = new PatchRequest(
                List.of(new PatchFileChange("src/Bin.class", binaryB64, "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID, request))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.BINARY_CONTENT);
    }

    @Test
    @DisplayName("rejects an archive file")
    void rejectsArchive() {
        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf("bundle.tar.gz", "data", false)))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.ARCHIVE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("a single rejected file aborts the whole request — no file is applied")
    void oneRejectedEntryAppliesNothing() {
        PatchRequest request = new PatchRequest(
                List.of(
                        new PatchFileChange("src/Good.java", b64("class Good {}"), "base64"),
                        new PatchFileChange("../escape.java", b64("class Bad {}"), "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID, request))
                .isInstanceOf(PatchRejectedException.class);

        assertThat(Files.exists(root.resolve("src/Good.java"))).isFalse();
    }
}
