package com.vibegraph.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.ProjectUsage;
import com.vibegraph.auth.repository.ProjectUsageRepository;

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
        long safeBytes = Math.max(0L, bytes);
        ProjectUsage usage = projectUsageRepository.findById(projectId)
                .orElseGet(() -> ProjectUsage.builder()
                        .projectId(projectId)
                        .ownerId(ownerId)
                        .build());
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
    public void recordPatchDelta(String projectId, long deltaBytes) {
        ProjectUsage usage = projectUsageRepository.findById(projectId)
                .orElse(null);
        if (usage == null) {
            // No row yet (project was never imported through this path); nothing to update.
            log.warn("recordPatchDelta called for unknown project {}, skipping", projectId);
            return;
        }
        long updated = Math.max(0L, usage.getStorageBytes() + deltaBytes);
        usage.setStorageBytes(updated);
        projectUsageRepository.save(usage);
        log.debug("Recorded patch delta {} bytes for project {} -> new total {} bytes",
                deltaBytes, projectId, updated);
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
}
