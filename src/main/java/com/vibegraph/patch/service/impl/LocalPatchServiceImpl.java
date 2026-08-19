package com.vibegraph.patch.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.graph.service.ImportCreditBilling;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.patch.config.LocalPatchProperties;
import com.vibegraph.patch.dto.request.PatchRequest;
import com.vibegraph.patch.dto.request.PatchRequest.PatchDeletion;
import com.vibegraph.patch.dto.request.PatchRequest.PatchFileChange;
import com.vibegraph.patch.dto.response.PatchResult;
import com.vibegraph.patch.exception.PatchRejectedException;
import com.vibegraph.patch.exception.PatchRejectedException.Reason;
import com.vibegraph.patch.service.PatchAnalysisScheduler;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Filesystem implementation of
 * {@link com.vibegraph.patch.service.LocalPatchService}.
 *
 * <p>
 * Resolves the project's on-disk root via the hardened
 * {@link SourceFileService#resolveProjectRoot(String)} (already
 * {@code toRealPath}-resolved), then
 * validates <em>every</em> entry before writing/deleting anything. Any
 * violation aborts the whole
 * request with {@link PatchRejectedException} so no partial state is left on
 * disk.
 *
 * <p>
 * No graph mutation is performed inline here: there is no safe per-file incremental parser,
 * so a successful write schedules background re-analysis and the result flags
 * {@code requiresAnalyze=true}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalPatchServiceImpl implements com.vibegraph.patch.service.LocalPatchService {

    static final int MAX_PATH_LENGTH = 1024;
    private static final int BINARY_SCAN_BYTES = 8192;
    /** Writes are confined to Java sources - the only content the knowledge graph needs. */
    private static final String JAVA_EXTENSION = ".java";

    private final SourceFileService sourceFileService;

    private final LocalPatchProperties properties;
    private final AccountSettingsService accountSettingsService;
    private final ProjectUsageService projectUsageService;
    private final ImportCreditBilling importCreditBilling;
    private final FeatureGateService featureGateService;
    private final CurrentUser currentUser;
    private final AtomicPatchApplier atomicPatchApplier;

    @Autowired(required = false)
    private PatchAnalysisScheduler patchAnalysisScheduler;

    /**
     * Directory segments that must never be written to or deleted through a patch.
     */
    private static final Set<String> BLOCKED_DIR_SEGMENTS = Set.of(
            ".git", "node_modules", "dist", "build", "target", "out", "bin");

    /** Exact filenames that are always refused (SSH keys, dotenv). */
    private static final Set<String> BLOCKED_FILENAMES = Set.of(
            ".env", "id_rsa", "id_dsa", "id_ed25519");

    /**
     * Extensions that always carry secrets and are refused regardless of content.
     */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("pem", "key");

    /**
     * Archive suffixes (checked against the full lowercased filename to catch
     * {@code .tar.gz}).
     */
    private static final List<String> BLOCKED_ARCHIVE_SUFFIXES = List.of(
            ".zip", ".tar", ".tar.gz", ".tgz", ".rar", ".7z");

    @Override
    @Transactional
    public PatchResult applyPatch(String projectId, PatchRequest request) {
        featureGateService.assertEnabled(FeatureGateService.CLI_PUSH);
        PatchRequest patch = request == null
                ? new PatchRequest(List.of(), List.of(), false)
                : request;

        // Root is real-path resolved and confined; throws ProjectNotFoundException
        // (404) if unknown.
        Path root = sourceFileService.resolveProjectRoot(projectId);

        List<PatchFileChange> files = patch.safeFiles();
        List<PatchDeletion> deletions = patch.safeDeletions();

        enforceCountLimits(files, deletions);
        UUID userId = currentUser.id();
        projectUsageService.lockForPatch(projectId, userId);

        // --- Phase 1: validate EVERYTHING (fail-fast). No filesystem mutation happens
        // here. ---
        long totalBytes = 0;
        byte[][] decoded = new byte[files.size()][];
        Path[] fileTargets = new Path[files.size()];
        Long[] oldSizes = new Long[files.size()];
        for (int i = 0; i < files.size(); i++) {
            PatchFileChange file = files.get(i);
            Path target = validateRelativePath(root, file.path());
            // Writes are .java-only, mirroring the archive/GitHub importers. Deletions stay
            // unrestricted so legacy non-Java files pushed before this rule can be removed.
            enforceJavaSource(target);
            byte[] bytes = decodeContent(file);
            enforceFileSize(bytes.length);
            totalBytes = addExact(totalBytes, bytes.length);
            if (totalBytes > properties.getMaxTotalBytes()) {
                throw new PatchRejectedException(Reason.TOTAL_TOO_LARGE,
                        "cumulative content exceeds the maximum total bytes");
            }
            rejectIfBinary(bytes);
            decoded[i] = bytes;
            fileTargets[i] = target;
            oldSizes[i] = Files.isRegularFile(target) ? target.toFile().length() : 0L;
        }

        Path[] deletionTargets = new Path[deletions.size()];
        long[] deletionOldSizes = new long[deletions.size()];
        for (int i = 0; i < deletions.size(); i++) {
            deletionTargets[i] = validateRelativePath(root, deletions.get(i).path());
            // Capture existing size before any deletion (0 if file does not exist).
            deletionOldSizes[i] = Files.isRegularFile(deletionTargets[i])
                    ? deletionTargets[i].toFile().length() : 0L;
        }
        rejectDuplicateAndOverlappingTargets(fileTargets, deletionTargets);

        // Quota: net delta = sum(newSize - oldSize) for writes + sum(-oldSize) for deletions.
        long netDeltaBytes = 0;
        for (int i = 0; i < files.size(); i++) {
            netDeltaBytes = addExact(
                    netDeltaBytes,
                    Math.subtractExact((long) decoded[i].length, oldSizes[i]));
        }
        for (long deletionOldSize : deletionOldSizes) {
            netDeltaBytes = Math.subtractExact(netDeltaBytes, deletionOldSize);
        }
        // assertQuotaNotExceeded skips automatically when delta <= 0 (no new storage consumed).
        accountSettingsService.assertQuotaNotExceeded(userId, netDeltaBytes);

        // --- Phase 2: dry run reports would-be counts without touching the filesystem.
        // ---
        if (patch.dryRun()) {
            int wouldDelete = 0;
            for (Path target : deletionTargets) {
                if (Files.isRegularFile(target)) {
                    wouldDelete++;
                }
            }
            log.info("Local patch dry-run for project {}: {} file(s), {} deletion(s)",
                    projectId, files.size(), wouldDelete);
            return new PatchResult(projectId, files.size(), wouldDelete, List.of(), false);
        }

        long requiredCredits = importCreditBilling.chargeUpfront(
                userId, ImportCreditBilling.OPERATION_CLI_PUSH, files.size(), projectId, "CLI");

        List<AtomicPatchApplier.Write> writes = new java.util.ArrayList<>(fileTargets.length);
        for (int index = 0; index < fileTargets.length; index++) {
            writes.add(new AtomicPatchApplier.Write(fileTargets[index], decoded[index]));
        }
        List<Path> existingDeletions = java.util.Arrays.stream(deletionTargets)
                .filter(Files::isRegularFile)
                .toList();

        AtomicPatchApplier.Session patchSession =
                atomicPatchApplier.apply(root, writes, existingDeletions);
        boolean requiresAnalyze = changedOrDeleted(writes.size(), existingDeletions.size());
        try {
            if (netDeltaBytes != 0) {
                projectUsageService.recordPatchDelta(projectId, userId, netDeltaBytes);
            }
            completePatchSession(patchSession, projectId, requiresAnalyze);
        } catch (RuntimeException ex) {
            patchSession.rollback();
            throw ex;
        }

        int changed = writes.size();
        int deleted = existingDeletions.size();
        log.info("Local patch for project {}: {} changed, {} deleted (requiresAnalyze={}, delta={}B, credits={})",
                projectId, changed, deleted, requiresAnalyze, netDeltaBytes, requiredCredits);
        return new PatchResult(projectId, changed, deleted, List.of(), requiresAnalyze);
    }

    // --- count / size limits
    // -------------------------------------------------------------------

    private void enforceCountLimits(List<PatchFileChange> files, List<PatchDeletion> deletions) {
        if (files.size() > properties.getMaxFiles() || deletions.size() > properties.getMaxFiles()) {
            throw new PatchRejectedException(Reason.TOO_MANY_FILES,
                    "request exceeds the maximum number of entries (" + properties.getMaxFiles() + ")");
        }
    }

    private void enforceFileSize(int decodedLength) {
        if (decodedLength > properties.getMaxFileBytes()) {
            throw new PatchRejectedException(Reason.FILE_TOO_LARGE,
                    "file exceeds the maximum size of " + properties.getMaxFileBytes() + " bytes");
        }
    }

    private long addExact(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Patch size is outside the supported range", ex);
        }
    }

    private void rejectDuplicateAndOverlappingTargets(Path[] writes, Path[] deletions) {
        List<Path> targets = new java.util.ArrayList<>(writes.length + deletions.length);
        targets.addAll(java.util.Arrays.asList(writes));
        targets.addAll(java.util.Arrays.asList(deletions));
        Set<Path> unique = new HashSet<>();
        for (Path target : targets) {
            if (!unique.add(target)) {
                throw new PatchRejectedException(
                        Reason.DUPLICATE_PATH, "patch contains duplicate target paths");
            }
        }
        List<Path> sorted = unique.stream().sorted().toList();
        for (int index = 0; index < sorted.size() - 1; index++) {
            if (sorted.get(index + 1).startsWith(sorted.get(index))) {
                throw new PatchRejectedException(
                        Reason.OVERLAPPING_PATH, "patch contains overlapping target paths");
            }
        }
    }

    // --- content decoding & binary detection
    // ---------------------------------------------------

    private byte[] decodeContent(PatchFileChange file) {
        String encoding = file.encoding();
        if (encoding != null && !encoding.isBlank() && !encoding.trim().equalsIgnoreCase("base64")) {
            throw new PatchRejectedException(Reason.UNSUPPORTED_ENCODING,
                    "only base64 content encoding is supported");
        }
        String content = file.contentBase64();
        if (content == null) {
            throw new PatchRejectedException(Reason.MISSING_CONTENT, "contentBase64 is required");
        }
        // Cheap guard before decoding so an oversized string cannot balloon memory on
        // decode.
        if ((long) content.length() > properties.getMaxFileBytes() * 2) {
            throw new PatchRejectedException(Reason.FILE_TOO_LARGE,
                    "file exceeds the maximum size of " + properties.getMaxFileBytes() + " bytes");
        }
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException ex) {
            throw new PatchRejectedException(Reason.INVALID_BASE64, "content is not valid base64");
        }
    }

    private void rejectIfBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, BINARY_SCAN_BYTES);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                throw new PatchRejectedException(Reason.BINARY_CONTENT,
                        "binary content is not permitted");
            }
        }
    }

    // --- path validation
    // -----------------------------------------------------------------------

    /**
     * Validate a caller-supplied path as a relative POSIX path confined to
     * {@code root}, and reject
     * blocked directories/files/archives. Returns the resolved, contained absolute
     * path.
     */
    private Path validateRelativePath(Path root, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new PatchRejectedException(Reason.BLANK_PATH, "path is required");
        }
        if (rawPath.length() > MAX_PATH_LENGTH) {
            throw new PatchRejectedException(Reason.PATH_TOO_LONG, "path is too long");
        }
        if (rawPath.chars().anyMatch(Character::isISOControl)) {
            throw new PatchRejectedException(Reason.INVALID_PATH, "path contains control characters");
        }
        if (rawPath.indexOf('\\') >= 0) {
            throw new PatchRejectedException(Reason.BACKSLASH_PATH, "path must use POSIX '/' separators");
        }
        // A ':' only appears in Windows drive paths (C:) or URI schemes — never in a
        // relative POSIX path.
        if (rawPath.indexOf(':') >= 0) {
            throw new PatchRejectedException(Reason.DRIVE_PATH, "path must not contain a drive or scheme");
        }
        if (rawPath.startsWith("/")) {
            throw new PatchRejectedException(Reason.ABSOLUTE_PATH, "path must be relative");
        }

        String[] segments = rawPath.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".")) {
                throw new PatchRejectedException(Reason.INVALID_PATH, "path has empty or '.' segments");
            }
            if (segment.equals("..")) {
                throw new PatchRejectedException(Reason.PATH_TRAVERSAL, "path must not contain '..'");
            }
            if (BLOCKED_DIR_SEGMENTS.contains(segment.toLowerCase(Locale.ROOT))) {
                throw new PatchRejectedException(Reason.BLOCKED_DIRECTORY,
                        "path targets a protected directory");
            }
        }

        String fileName = segments[segments.length - 1].toLowerCase(Locale.ROOT);
        if (BLOCKED_FILENAMES.contains(fileName) || fileName.startsWith(".env")) {
            throw new PatchRejectedException(Reason.BLOCKED_FILE, "file is not permitted");
        }
        for (String suffix : BLOCKED_ARCHIVE_SUFFIXES) {
            if (fileName.endsWith(suffix)) {
                throw new PatchRejectedException(Reason.ARCHIVE_NOT_ALLOWED, "archives are not permitted");
            }
        }
        String extension = extensionOf(fileName);
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new PatchRejectedException(Reason.BLOCKED_FILE, "file is not permitted");
        }

        Path resolved;
        try {
            resolved = root.resolve(rawPath).normalize();
        } catch (InvalidPathException ex) {
            throw new PatchRejectedException(Reason.INVALID_PATH, "path is not valid");
        }
        if (!resolved.startsWith(root)) {
            throw new PatchRejectedException(Reason.PATH_ESCAPE, "path escapes the project root");
        }
        rejectSymlinkEscape(root, resolved);
        return resolved;
    }

    /**
     * Defense in depth against symlink escape: the nearest existing ancestor of
     * {@code target}
     * (and the target itself, if it exists) must have a real path that stays within
     * {@code root}.
     */
    private void rejectSymlinkEscape(Path root, Path target) {
        Path existing = target;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return;
        }
        try {
            Path real = existing.toRealPath();
            if (!real.startsWith(root)) {
                throw new PatchRejectedException(Reason.SYMLINK_ESCAPE, "path escapes the project root");
            }
        } catch (IOException ex) {
            throw new PatchRejectedException(Reason.INVALID_PATH, "path could not be resolved");
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1);
    }

    /**
     * Refuse writes whose target is not a {@code .java} file. The extension is matched
     * case-sensitively: a Java compilation unit is always {@code *.java}, and {@code .JAVA}
     * would only smuggle a non-source file past this rule.
     */
    private void enforceJavaSource(Path target) {
        Path fileName = target.getFileName();
        if (fileName == null || !fileName.toString().endsWith(JAVA_EXTENSION)) {
            throw new PatchRejectedException(Reason.NOT_JAVA_SOURCE,
                    "only .java source files can be pushed");
        }
    }

    private boolean changedOrDeleted(int changed, int deleted) {
        return changed > 0 || deleted > 0;
    }

    private void completePatchSession(
            AtomicPatchApplier.Session patchSession,
            String projectId,
            boolean requiresAnalyze) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            patchSession.commit();
            scheduleAnalysis(projectId, requiresAnalyze);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    patchSession.commit();
                    scheduleAnalysis(projectId, requiresAnalyze);
                } else {
                    patchSession.rollback();
                }
            }
        });
    }

    private void scheduleAnalysis(String projectId, boolean requiresAnalyze) {
        if (!requiresAnalyze || patchAnalysisScheduler == null) {
            return;
        }
        try {
            patchAnalysisScheduler.schedule(projectId);
        } catch (RuntimeException ex) {
            log.warn("Could not schedule analysis for CLI patch {}: {}", projectId, ex.getMessage());
        }
    }
}
