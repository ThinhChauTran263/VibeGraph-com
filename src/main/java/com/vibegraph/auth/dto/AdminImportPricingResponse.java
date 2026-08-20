package com.vibegraph.auth.dto;

import java.util.List;

/**
 * Admin-facing view of one import method's tiered pricing configuration.
 *
 * @param operationCode tier set key (IMPORT_ARCHIVE / IMPORT_GITHUB / CLI_PUSH)
 * @param tiers         tiers ordered by ascending file bound; {@code maxFiles == null} is the unlimited top tier
 */
public record AdminImportPricingResponse(
        String operationCode,
        List<Tier> tiers) {

    public record Tier(String tierCode, Integer maxFiles, int credits) {
    }
}
