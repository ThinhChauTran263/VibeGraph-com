package com.vibegraph.patch.service.impl;

import com.vibegraph.auth.CurrentUser;

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
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.auth.service.CreditPricingService;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.FeatureGateService;
import java.util.UUID;
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
    private AccountSettingsService accountSettingsService;
    private ProjectUsageService projectUsageService;
    private CreditPricingService creditPricingService;
    private CreditBalanceService creditBalanceService;
    private FeatureGateService featureGateService;
    private CurrentUser currentUser;
    private LocalPatchServiceImpl service;

    @BeforeEach
    void setUp() {
        sourceFileService = Mockito.mock(SourceFileService.class);
        accountSettingsService = Mockito.mock(AccountSettingsService.class);
        projectUsageService = Mockito.mock(ProjectUsageService.class);
        creditPricingService = Mockito.mock(CreditPricingService.class);
        creditBalanceService = Mockito.mock(CreditBalanceService.class);
        featureGateService = Mockito.mock(FeatureGateService.class);
        currentUser = Mockito.mock(CurrentUser.class);

        when(sourceFileService.resolveProjectRoot(PROJECT_ID)).thenReturn(root);
        service = new LocalPatchServiceImpl(sourceFileService, new LocalPatchProperties(), accountSettingsService, projectUsageService, creditPricingService, creditBalanceService, featureGateService, currentUser, new AtomicPatchApplier());
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("disabled CLI push blocks before filesystem resolution for real and dry-run requests")
    void cliPushDisabledBlocksBeforeFilesystemResolution(boolean dryRun) {
        Mockito.doThrow(new com.vibegraph.common.exception.FeatureDisabledException("cli.push"))
                .when(featureGateService).assertEnabled("cli.push");

        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf("src/Disabled.java", "class Disabled {}", dryRun)))
                .isInstanceOf(com.vibegraph.common.exception.FeatureDisabledException.class);

        Mockito.verify(sourceFileService, Mockito.never()).resolveProjectRoot(PROJECT_ID);
        Mockito.verifyNoInteractions(accountSettingsService, projectUsageService,
                creditPricingService, creditBalanceService);
    }

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
    @DisplayName("rejects duplicate write targets before debit or filesystem mutation")
    void rejectsDuplicateWriteTargets() {
        PatchRequest request = new PatchRequest(
                List.of(
                        new PatchFileChange("src/A.java", b64("one"), "base64"),
                        new PatchFileChange("src/A.java", b64("two"), "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID, request))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.DUPLICATE_PATH);
        assertThat(Files.exists(root.resolve("src/A.java"))).isFalse();
        Mockito.verify(creditBalanceService, Mockito.never())
                .deductCredits(Mockito.any(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @Test
    @DisplayName("rejects a target present in both writes and deletions")
    void rejectsWriteDeletionOverlap() {
        PatchRequest request = new PatchRequest(
                List.of(new PatchFileChange("src/A.java", b64("one"), "base64")),
                List.of(new PatchDeletion("src/A.java")),
                false);

        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID, request))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.DUPLICATE_PATH);
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

    // --- additional coverage requested by Agent 4.7 security matrix ----------------------------

    @Test
    @DisplayName("rejects id_rsa by exact filename")
    void rejectsIdRsaByFilename() {
        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf(".ssh/id_rsa", "PRIVATE", false)))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.BLOCKED_FILE);
    }

    @Test
    @DisplayName("rejects .env.local (dotfile-prefix branch)")
    void rejectsDotEnvLocal() {
        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf(".env.local", "SECRET=1", false)))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.BLOCKED_FILE);
    }

    @Test
    @DisplayName("rejects Windows-style backslash traversal")
    void rejectsBackslashPath() {
        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID,
                changeOf("..\\..\\evil.java", "x", false)))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.BACKSLASH_PATH);
    }

    @Test
    @DisplayName("rejects a file that exceeds the per-file size limit")
    void rejectsFileTooLarge() {
        LocalPatchProperties tight = new LocalPatchProperties();
        tight.setMaxFileBytes(64);
        LocalPatchServiceImpl bounded = new LocalPatchServiceImpl(sourceFileService, tight, accountSettingsService, projectUsageService, creditPricingService, creditBalanceService, featureGateService, currentUser, new AtomicPatchApplier());

        String big = "a".repeat(128); // 128 raw bytes → decoded > 64
        PatchRequest request = new PatchRequest(
                List.of(new PatchFileChange("src/Big.java", b64(big), "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> bounded.applyPatch(PROJECT_ID, request))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("rejects a request that exceeds the cumulative total-bytes limit")
    void rejectsTotalTooLarge() {
        LocalPatchProperties tight = new LocalPatchProperties();
        tight.setMaxFileBytes(64);
        tight.setMaxTotalBytes(80);
        LocalPatchServiceImpl bounded = new LocalPatchServiceImpl(sourceFileService, tight, accountSettingsService, projectUsageService, creditPricingService, creditBalanceService, featureGateService, currentUser, new AtomicPatchApplier());

        String chunk = "a".repeat(50); // two files, 50 + 50 = 100 > 80 total
        PatchRequest request = new PatchRequest(
                List.of(
                        new PatchFileChange("src/A.java", b64(chunk), "base64"),
                        new PatchFileChange("src/B.java", b64(chunk), "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> bounded.applyPatch(PROJECT_ID, request))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.TOTAL_TOO_LARGE);

        assertThat(Files.exists(root.resolve("src/A.java"))).isFalse();
        assertThat(Files.exists(root.resolve("src/B.java"))).isFalse();
    }

    @Test
    @DisplayName("rejects a request that exceeds the maximum number of files")
    void rejectsTooManyFiles() {
        LocalPatchProperties tight = new LocalPatchProperties();
        tight.setMaxFiles(2);
        LocalPatchServiceImpl bounded = new LocalPatchServiceImpl(sourceFileService, tight, accountSettingsService, projectUsageService, creditPricingService, creditBalanceService, featureGateService, currentUser, new AtomicPatchApplier());

        PatchRequest request = new PatchRequest(
                List.of(
                        new PatchFileChange("src/A.java", b64("A"), "base64"),
                        new PatchFileChange("src/B.java", b64("B"), "base64"),
                        new PatchFileChange("src/C.java", b64("C"), "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> bounded.applyPatch(PROJECT_ID, request))
                .isInstanceOf(PatchRejectedException.class)
                .extracting("reason").isEqualTo(Reason.TOO_MANY_FILES);
    }

    @Test
    @DisplayName("rejects a symlink escape via toRealPath (nearest existing ancestor)")
    void rejectsSymlinkEscape() throws Exception {
        // Create an outside directory containing the real secret.
        Path outside = Files.createTempDirectory("vibegraph-outside-");
        try {
            // Inside the project root, place a symlink dir "src/escape" that points to `outside`.
            Path linkDir = root.resolve("src");
            Files.createDirectories(linkDir);
            Path link = linkDir.resolve("escape");
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException notPermitted) {
                // Windows without Developer Mode + non-admin has no symlink privilege — skip cleanly.
                return;
            }

            PatchRequest request = new PatchRequest(
                    List.of(new PatchFileChange("src/escape/Owned.java", b64("owned"), "base64")),
                    List.of(),
                    false);

            assertThatThrownBy(() -> service.applyPatch(PROJECT_ID, request))
                    .isInstanceOf(PatchRejectedException.class)
                    .extracting("reason").isEqualTo(Reason.SYMLINK_ESCAPE);

            // Nothing was written outside the root.
            assertThat(Files.exists(outside.resolve("Owned.java"))).isFalse();
        } finally {
            // Best-effort cleanup — leave real files intact.
            try {
                Files.deleteIfExists(outside);
            } catch (Exception ignored) {
                // temp — OS cleanup will handle it
            }
        }
    }
}
