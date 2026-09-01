package com.vibegraph.auth.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.entity.ImportPricingTier;
import com.vibegraph.auth.dto.AdminImportPricingResponse;
import com.vibegraph.auth.dto.AdminImportPricingUpdateRequest;
import com.vibegraph.auth.repository.ImportPricingTierRepository;

import lombok.RequiredArgsConstructor;

/**
 * Admin management of the tiered import pricing table. One tier set exists
 * per import method; saving replaces the whole set atomically so the
 * thresholds/credits of a method are always internally consistent.
 */
@Service
@RequiredArgsConstructor
public class AdminImportPricingManagementService {

    /** The only operations billed through import_pricing_tiers. */
    public static final List<String> SUPPORTED_OPERATIONS = List.of(
            "IMPORT_ARCHIVE", "IMPORT_GITHUB", "CLI_PUSH");

    private final ImportPricingTierRepository tierRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AdminImportPricingResponse> listAll() {
        return SUPPORTED_OPERATIONS.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminImportPricingResponse get(String operationCode) {
        requireSupported(operationCode);
        return toResponse(operationCode);
    }

    /**
     * Replaces the tier set of one operation. Tiers must arrive with strictly
     * ascending {@code maxFiles}; only the last tier may be unlimited (null).
     */
    @Transactional
    public AdminImportPricingResponse replace(String operationCode, AdminImportPricingUpdateRequest request) {
        requireSupported(operationCode);
        validate(operationCode, request.tiers());

        tierRepository.deleteByOperationCode(operationCode);
        tierRepository.flush(); // Force DELETES to execute before INSERTS to prevent unique constraint violation
        List<ImportPricingTier> saved = new ArrayList<>();
        int sortOrder = 10;
        for (AdminImportPricingUpdateRequest.Tier tier : request.tiers()) {
            saved.add(tierRepository.save(ImportPricingTier.builder()
                    .operationCode(operationCode)
                    .tierCode(tier.tierCode().trim().toUpperCase())
                    .maxFiles(tier.maxFiles())
                    .credits(tier.credits())
                    .sortOrder(sortOrder)
                    .build()));
            sortOrder += 10;
        }

        auditService.recordCurrentUser("IMPORT_PRICING_UPDATE", null, "IMPORT_PRICING_TIER",
                operationCode, auditDetails(saved));
        return toResponse(operationCode);
    }

    private AdminImportPricingResponse toResponse(String operationCode) {
        List<AdminImportPricingResponse.Tier> tiers =
                tierRepository.findByOperationCodeOrderBySortOrderAsc(operationCode).stream()
                        .map(tier -> new AdminImportPricingResponse.Tier(
                                tier.getTierCode(), tier.getMaxFiles(), tier.getCredits()))
                        .toList();
        return new AdminImportPricingResponse(operationCode, tiers);
    }

    private void requireSupported(String operationCode) {
        if (!SUPPORTED_OPERATIONS.contains(operationCode)) {
            throw new IllegalArgumentException("Operation is not billed by import tiers: " + operationCode);
        }
    }

    private void validate(String operationCode, List<AdminImportPricingUpdateRequest.Tier> tiers) {
        Set<String> seenCodes = new HashSet<>();
        Integer previousMax = null;
        for (int index = 0; index < tiers.size(); index++) {
            AdminImportPricingUpdateRequest.Tier tier = tiers.get(index);
            String code = tier.tierCode().trim().toUpperCase();
            if (!seenCodes.add(code)) {
                throw new IllegalArgumentException("Duplicate tier code: " + code);
            }
            if (tier.maxFiles() == null) {
                if (index != tiers.size() - 1) {
                    throw new IllegalArgumentException(
                            "Only the last tier may be unlimited (maxFiles omitted)");
                }
                continue;
            }
            if (previousMax != null && tier.maxFiles() <= previousMax) {
                throw new IllegalArgumentException(
                        "Tier maxFiles must be strictly ascending for operation: " + operationCode);
            }
            previousMax = tier.maxFiles();
        }
    }

    private Map<String, Object> auditDetails(List<ImportPricingTier> saved) {
        Map<String, Object> details = new LinkedHashMap<>();
        List<Map<String, Object>> tiers = new ArrayList<>();
        for (ImportPricingTier tier : saved) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tierCode", tier.getTierCode());
            entry.put("maxFiles", tier.getMaxFiles());
            entry.put("credits", tier.getCredits());
            tiers.add(entry);
        }
        details.put("tiers", tiers);
        return details;
    }
}
