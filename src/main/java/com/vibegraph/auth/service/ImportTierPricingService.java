package com.vibegraph.auth.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.entity.ImportPricingTier;
import com.vibegraph.auth.repository.ImportPricingTierRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tiered pricing lookup for import/analyze operations. Each operation owns
 * an ordered set of {@link ImportPricingTier}s; the charge is the credit
 * value of the first tier whose {@code maxFiles} covers the imported
 * {@code .java} file count. All thresholds and credits are admin-managed —
 * nothing about the numbers lives in code.
 */
@Service
@RequiredArgsConstructor
public class ImportTierPricingService {

    private final ImportPricingTierRepository tierRepository;

    /**
     * Returns the credits an operation costs for the given file count.
     *
     * @throws IllegalStateException when no tiers are configured for the
     *         operation (fail closed, mirroring {@code CreditPricingService})
     */
    @Transactional(readOnly = true)
    public long calculateCredits(String operationCode, int fileCount) {
        if (fileCount < 0) {
            throw new IllegalArgumentException("fileCount must be non-negative");
        }

        List<ImportPricingTier> tiers = tierRepository.findByOperationCodeOrderBySortOrderAsc(operationCode);
        if (tiers.isEmpty()) {
            throw new IllegalStateException("No pricing tiers configured for operation: " + operationCode);
        }

        // Defensive re-sort by bound (null = unlimited last): sort_order is the admin's
        // display order, but correctness must not depend on it being entered sanely.
        return tiers.stream()
                .sorted(Comparator.comparing(ImportPricingTier::getMaxFiles,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .filter(tier -> tier.getMaxFiles() == null || fileCount <= tier.getMaxFiles())
                .findFirst()
                .map(ImportPricingTier::getCredits)
                .map(Integer::longValue)
                .orElseThrow(() -> new IllegalStateException(
                        "No pricing tier covers file count " + fileCount
                                + " for operation: " + operationCode));
    }
}
