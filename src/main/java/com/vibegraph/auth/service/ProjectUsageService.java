package com.vibegraph.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.ProjectUsage;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.auth.repository.UserAccountSettingsRepository;
import com.vibegraph.common.exception.QuotaExceededException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the {@code project_usage} table that tracks each project's source-storage footprint.
 *
 * <p>All mutations are transactional. Usage is never allowed to go below zero.
 *
 * <p>Callers must have already verified quota (via
 * {@link AccountSettingsService#assertQuotaNotExceeded}) and blocked-account status before
 * invoking {@link #recordImport} or {@link #recordPatchDelta}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectUsageService {

    private final ProjectUsageRepository projectUsageRepository;
    private final UserAccountSettingsRepository settingsRepository;

    /**
     * Record storage usage after a successful project import (local, archive, or GitHub).
     * Creates a new {@link ProjectUsage} row if none exists, or replaces the existing value
     * with the full {@code bytes} of the imported source tree.
     *
     * @param projectId the project identifier
     * @param ownerId   the owning user's UUID
     * @param bytes     the total source-storage size in bytes (must be &ge; 0)
     */
    @Transactional
    public void recordImport(String projectId, UUID ownerId, long bytes) {
        UserAccountSettings settings = lockSettings(ownerId);
        ProjectUsage usage = projectUsageRepository.findById(projectId)
                .orElseGet(() -> ProjectUsage.builder()
                        .projectId(projectId)
                        .ownerId(ownerId)
                        .build());
        verifyOwner(usage, ownerId);
        long safeBytes = requireNonNegative(bytes);
        assertWithinQuota(ownerId, settings, usage.getStorageBytes(), safeBytes);
        usage.setStorageBytes(safeBytes);
        projectUsageRepository.save(usage);
        log.debug("Recorded import usage for project {}: {} bytes", projectId, safeBytes);
    }

    /**
     * Adjust the stored usage after a local patch operation by adding {@code deltaBytes}.
     * The delta can be negative (replacement by a smaller file, or deletions).
     * The result is clamped to zero — usage never goes negative.
     *
     * <p>For a <em>replace</em> operation, {@code deltaBytes = newSize - oldSize}.
     * For a <em>new</em> file, {@code deltaBytes = newSize}.
     * For a <em>deletion</em>, {@code deltaBytes = -oldSize}.
     *
     * @param projectId  the project identifier
     * @param deltaBytes the signed byte change (may be negative)
     */
    @Transactional
    public void recordPatchDelta(String projectId, UUID ownerId, long deltaBytes) {
        UserAccountSettings settings = lockSettings(ownerId);
        ProjectUsage usage = requireLockedUsage(projectId);
        verifyOwner(usage, ownerId);
        long updated = safeAddAndClamp(usage.getStorageBytes(), deltaBytes);
        assertWithinQuota(ownerId, settings, usage.getStorageBytes(), updated);
        usage.setStorageBytes(updated);
        projectUsageRepository.save(usage);
        log.debug("Recorded patch delta {} bytes for project {} -> new total {} bytes",
                deltaBytes, projectId, updated);
    }

    /** Backward-compatible overload; ownership is resolved from the existing usage row. */
    @Transactional
    public void recordPatchDelta(String projectId, long deltaBytes) {
        ProjectUsage usage = projectUsageRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project storage usage is not registered: " + projectId));
        recordPatchDelta(projectId, usage.getOwnerId(), deltaBytes);
    }

    @Transactional
    public void lockForPatch(String projectId, UUID ownerId) {
        lockSettings(ownerId);
        verifyOwner(requireLockedUsage(projectId), ownerId);
    }

    /**
     * Remove the usage record for a deleted project, effectively freeing its storage quota.
     * If no row exists, this is a no-op.
     *
     * @param projectId the project identifier
     */
    @Transactional
    public void recordDeletion(String projectId) {
        if (projectUsageRepository.existsById(projectId)) {
            projectUsageRepository.deleteById(projectId);
            log.debug("Deleted usage record for project {}", projectId);
        }
    }

    private UserAccountSettings lockSettings(UUID ownerId) {
        return settingsRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Account settings not found"));
    }

    private ProjectUsage requireLockedUsage(String projectId) {
        return projectUsageRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project storage usage is not registered: " + projectId));
    }

    private void verifyOwner(ProjectUsage usage, UUID ownerId) {
        if (!ownerId.equals(usage.getOwnerId())) {
            throw new IllegalArgumentException("Project usage owner mismatch");
        }
    }

    private void assertWithinQuota(
            UUID ownerId, UserAccountSettings settings, long oldProjectBytes, long newProjectBytes) {
        long aggregateBytes = projectUsageRepository.sumStorageBytesByOwnerId(ownerId);
        long usageWithoutProject;
        try {
            usageWithoutProject = Math.subtractExact(aggregateBytes, oldProjectBytes);
            long projectedUsage = Math.addExact(usageWithoutProject, newProjectBytes);
            long limitBytes = AccountSettingsService.effectiveLimitBytes(settings);
            if (projectedUsage > limitBytes) {
                throw new QuotaExceededException(AccountSettingsService.QUOTA_EXCEEDED_MESSAGE);
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Source storage usage is outside the supported range", ex);
        }
    }

    private long requireNonNegative(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Storage usage must be non-negative");
        }
        return bytes;
    }

    private long safeAddAndClamp(long current, long delta) {
        try {
            return Math.max(0L, Math.addExact(current, delta));
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Source storage usage is outside the supported range", ex);
        }
    }
}
