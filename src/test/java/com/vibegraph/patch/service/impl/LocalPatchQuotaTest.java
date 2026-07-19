package com.vibegraph.patch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.InOrder;

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
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.common.exception.FeatureDisabledException;
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
    @Mock FeatureGateService featureGateService;
    @Mock CurrentUser currentUser;

    private LocalPatchServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(sourceFileService.resolveProjectRoot(PROJECT_ID)).thenReturn(root);
        org.mockito.Mockito.lenient().when(currentUser.id()).thenReturn(userId);

        service = new LocalPatchServiceImpl(sourceFileService, new LocalPatchProperties(), accountSettingsService, projectUsageService, creditPricingService, creditBalanceService, featureGateService, currentUser, new AtomicPatchApplier());
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Patch below quota → preflights credits, writes file, records delta, and deducts")
    void patchBelowQuota_preflightsBeforeWritesAndDeducts() {
        when(creditPricingService.calculateCredits("CLI_PUSH", 1, 0)).thenReturn(2L);
        PatchRequest req = new PatchRequest(
                List.of(new PatchFileChange("src/Hello.java", b64("class Hello {}"), "base64")),
                List.of(),
                false);

        PatchResult result = service.applyPatch(PROJECT_ID, req);

        assertThat(result.changed()).isEqualTo(1);
        assertThat(result.deleted()).isZero();
        verify(projectUsageService).recordPatchDelta(eq(PROJECT_ID), eq(userId), anyLong());
        InOrder order = inOrder(
                accountSettingsService, featureGateService, creditPricingService, creditBalanceService, projectUsageService);
        order.verify(featureGateService).assertEnabled(FeatureGateService.CLI_PUSH);
        order.verify(projectUsageService).lockForPatch(PROJECT_ID, userId);
        order.verify(accountSettingsService).assertQuotaNotExceeded(eq(userId), anyLong());
        order.verify(creditPricingService).calculateCredits("CLI_PUSH", 1, 0);
        order.verify(creditBalanceService)
                .deductCredits(userId, 2L, "CLI", "CLI_PUSH", PROJECT_ID);
        order.verify(projectUsageService).recordPatchDelta(eq(PROJECT_ID), eq(userId), anyLong());
    }

    @Test
    @DisplayName("Authoritative debit failure → no file write or usage update")
    void debitFailure_doesNotMutateFilesystem() {
        when(creditPricingService.calculateCredits("CLI_PUSH", 1, 0)).thenReturn(2L);
        doThrow(new com.vibegraph.common.exception.InsufficientCreditsException("Insufficient credits"))
                .when(creditBalanceService)
                .deductCredits(userId, 2L, "CLI", "CLI_PUSH", PROJECT_ID);
        PatchRequest req = new PatchRequest(
                List.of(new PatchFileChange("src/Blocked.java", b64("class Blocked {}"), "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID, req))
                .isInstanceOf(com.vibegraph.common.exception.InsufficientCreditsException.class);

        assertThat(Files.exists(root.resolve("src/Blocked.java"))).isFalse();
        verify(projectUsageService, never()).recordPatchDelta(eq(PROJECT_ID), anyLong());
    }

    @Test
    @DisplayName("CLI push feature disabled -> no debit, file write, or usage update")
    void cliPushFlagDisabled_blocksBeforeDebitAndWrite() {
        doThrow(new FeatureDisabledException(FeatureGateService.CLI_PUSH))
                .when(featureGateService).assertEnabled(FeatureGateService.CLI_PUSH);
        PatchRequest req = new PatchRequest(
                List.of(new PatchFileChange("src/Disabled.java", b64("class Disabled {}"), "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID, req))
                .isInstanceOf(FeatureDisabledException.class);

        assertThat(Files.exists(root.resolve("src/Disabled.java"))).isFalse();
        verify(creditPricingService, never()).calculateCredits(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), anyLong());
        verify(creditBalanceService, never()).deductCredits(
                eq(userId), anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(projectUsageService, never()).recordPatchDelta(eq(PROJECT_ID), anyLong());
    }

    @Test
    @DisplayName("Usage persistence failure → restores created and overwritten files")
    void usageFailure_rollsBackFilesystemBatch() throws Exception {
        Path existing = root.resolve("src/Existing.java");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "old");
        when(creditPricingService.calculateCredits("CLI_PUSH", 2, 0)).thenReturn(3L);
        doThrow(new IllegalStateException("usage failed"))
                .when(projectUsageService).recordPatchDelta(eq(PROJECT_ID), eq(userId), anyLong());
        PatchRequest req = new PatchRequest(
                List.of(
                        new PatchFileChange("src/Existing.java", b64("new-content"), "base64"),
                        new PatchFileChange("src/New.java", b64("created"), "base64")),
                List.of(),
                false);

        assertThatThrownBy(() -> service.applyPatch(PROJECT_ID, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("usage failed");

        assertThat(Files.readString(existing)).isEqualTo("old");
        assertThat(Files.exists(root.resolve("src/New.java"))).isFalse();
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
        verify(projectUsageService).recordPatchDelta(eq(PROJECT_ID), eq(userId), anyLong());
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
        // but usage and credits must NOT be persisted
        verify(projectUsageService, never()).recordPatchDelta(eq(PROJECT_ID), anyLong());
        verify(creditPricingService, never()).calculateCredits(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), anyLong());
        verify(creditBalanceService, never()).assertCreditsAvailable(eq(userId), anyLong());
        verify(creditBalanceService, never()).deductCredits(
                eq(userId), anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
