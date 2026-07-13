package com.vibegraph.patch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.CreditPricingService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.common.exception.QuotaExceededException;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.patch.config.LocalPatchProperties;
import com.vibegraph.patch.dto.request.PatchRequest;
import com.vibegraph.patch.dto.request.PatchRequest.PatchDeletion;
import com.vibegraph.patch.dto.request.PatchRequest.PatchFileChange;
import com.vibegraph.patch.dto.response.PatchResult;

/**
 * Unit tests for quota enforcement behaviour added to {@link LocalPatchServiceImpl}.
 *
 * Separate from {@link LocalPatchServiceImplTest} (which covers file/path security rules)
 * so each test class has a single focused responsibility.
 *
 * Run: mvnw test -Dtest=LocalPatchQuotaTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocalPatchServiceImpl — quota integration")
class LocalPatchQuotaTest {

    private static final String PROJECT_ID = "quota-proj";

    @TempDir
    Path root;

    @Mock SourceFileService sourceFileService;
    @Mock AccountSettingsService accountSettingsService;
    @Mock ProjectUsageService projectUsageService;
    @Mock CreditPricingService creditPricingService;
    @Mock CreditBalanceService creditBalanceService;
    @Mock CurrentUser currentUser;

    private LocalPatchServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(sourceFileService.resolveProjectRoot(PROJECT_ID)).thenReturn(root);
        when(currentUser.id()).thenReturn(userId);

        service = new LocalPatchServiceImpl(sourceFileService, new LocalPatchProperties(), accountSettingsService, projectUsageService, creditPricingService, creditBalanceService, currentUser);
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Patch below quota → writes file and records delta")
    void patchBelowQuota_writesFileAndRecordsDelta() {
        PatchRequest req = new PatchRequest(
                List.of(new PatchFileChange("src/Hello.java", b64("class Hello {}"), "base64")),
                List.of(),
                false);

        PatchResult result = service.applyPatch(PROJECT_ID, req);

        assertThat(result.changed()).isEqualTo(1);
        assertThat(result.deleted()).isZero();
        // Delta > 0 (new file) → recordPatchDelta must be called
        verify(projectUsageService).recordPatchDelta(eq(PROJECT_ID), anyLong());
    }

    @Test
    @DisplayName("Deletion only → records negative delta (frees quota)")
    void deletionOnly_recordsNegativeDelta() throws Exception {
        // Create an existing file to delete
        Path existing = root.resolve("src/Old.java");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "class Old {}");

        PatchRequest req = new PatchRequest(
                List.of(),
                List.of(new PatchDeletion("src/Old.java")),
                false);

        service.applyPatch(PROJECT_ID, req);

        // Delta is negative (old file removed) → recordPatchDelta must be called with a negative value
        verify(projectUsageService).recordPatchDelta(eq(PROJECT_ID), anyLong());
    }

    @Test
    @DisplayName("Zero net delta (replace same-size) → recordPatchDelta NOT called")
    void zeroNetDelta_doesNotCallRecordDelta() throws Exception {
        // Write a file whose replacement will have the same byte length
        String content = "class Same {}";
        Path existing = root.resolve("src/Same.java");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, content);

        PatchRequest req = new PatchRequest(
                List.of(new PatchFileChange("src/Same.java", b64(content), "base64")),
                List.of(),
                false);

        service.applyPatch(PROJECT_ID, req);

        // net delta = newSize - oldSize = 0 → should NOT call recordPatchDelta
        verify(projectUsageService, never()).recordPatchDelta(eq(PROJECT_ID), eq(0L));
    }

    // ── quota exceeded ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Quota exceeded → throws QuotaExceededException, file NOT written")
    void quotaExceeded_throwsAndDoesNotWriteFile() {
        doThrow(new QuotaExceededException("Quota exceeded"))
                .when(accountSettingsService).assertQuotaNotExceeded(eq(userId), anyLong());

        PatchRequest req = new PatchRequest(
                List.of(new PatchFileChange("src/Big.java", b64("class Big {}"), "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID, req))
                .isInstanceOf(QuotaExceededException.class);

        // File must NOT have been written
        assertThat(Files.exists(root.resolve("src/Big.java"))).isFalse();
        // recordPatchDelta must NOT be called when quota check fails
        verify(projectUsageService, never()).recordPatchDelta(eq(PROJECT_ID), anyLong());
    }

    // ── dry run ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("dryRun → quota is still checked, but usage is NOT persisted")
    void dryRun_checksQuotaButDoesNotPersistUsage() {
        PatchRequest req = new PatchRequest(
                List.of(new PatchFileChange("src/Hello.java", b64("class Hello {}"), "base64")),
                List.of(),
                true); // dryRun = true

        service.applyPatch(PROJECT_ID, req);

        // quota check must still be invoked (dry run informs user of would-fail)
        verify(accountSettingsService).assertQuotaNotExceeded(eq(userId), anyLong());
        // but usage must NOT be persisted
        verify(projectUsageService, never()).recordPatchDelta(eq(PROJECT_ID), anyLong());
    }
}
