package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.entity.ImportPricingTier;
import com.vibegraph.auth.repository.ImportPricingTierRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImportTierPricingService")
class ImportTierPricingServiceTest {

    @Mock ImportPricingTierRepository tierRepository;

    private static final List<ImportPricingTier> DEFAULT_TIERS = List.of(
            tier("IMPORT_ARCHIVE", "SMALL", 100, 2, 10),
            tier("IMPORT_ARCHIVE", "MEDIUM", 500, 5, 20),
            tier("IMPORT_ARCHIVE", "LARGE", 2000, 15, 30),
            tier("IMPORT_ARCHIVE", "XLARGE", null, 40, 40));

    @ParameterizedTest(name = "{0} files -> tier {1} = {2} credits")
    @MethodSource("tierBoundaries")
    @DisplayName("picks the first tier whose file bound covers the count (inclusive)")
    void calculateCredits_picksCoveringTier(int fileCount, String expectedTier, long expectedCredits) {
        ImportTierPricingService service = new ImportTierPricingService(tierRepository);
        when(tierRepository.findByOperationCodeOrderBySortOrderAsc("IMPORT_ARCHIVE"))
                .thenReturn(DEFAULT_TIERS);

        assertThat(service.calculateCredits("IMPORT_ARCHIVE", fileCount)).isEqualTo(expectedCredits);
    }

    static Stream<Arguments> tierBoundaries() {
        return Stream.of(
                Arguments.of(0, "SMALL", 2L),
                Arguments.of(100, "SMALL", 2L),
                Arguments.of(101, "MEDIUM", 5L),
                Arguments.of(500, "MEDIUM", 5L),
                Arguments.of(501, "LARGE", 15L),
                Arguments.of(2000, "LARGE", 15L),
                Arguments.of(2001, "XLARGE", 40L),
                Arguments.of(50_000, "XLARGE", 40L));
    }

    @Test
    @DisplayName("selection stays correct even when tiers are stored out of order")
    void calculateCredits_resortsByBound() {
        ImportTierPricingService service = new ImportTierPricingService(tierRepository);
        when(tierRepository.findByOperationCodeOrderBySortOrderAsc("IMPORT_GITHUB")).thenReturn(List.of(
                tier("IMPORT_GITHUB", "XLARGE", null, 40, 40),
                tier("IMPORT_GITHUB", "LARGE", 2000, 15, 30),
                tier("IMPORT_GITHUB", "SMALL", 100, 2, 10),
                tier("IMPORT_GITHUB", "MEDIUM", 500, 5, 20)));

        assertThat(service.calculateCredits("IMPORT_GITHUB", 250)).isEqualTo(5L);
    }

    @Test
    @DisplayName("no configured tiers fails closed")
    void calculateCredits_missingTiers_throws() {
        ImportTierPricingService service = new ImportTierPricingService(tierRepository);
        when(tierRepository.findByOperationCodeOrderBySortOrderAsc("CLI_PUSH"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.calculateCredits("CLI_PUSH", 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLI_PUSH");
    }

    @Test
    @DisplayName("negative file counts are rejected")
    void calculateCredits_negativeFileCount_throws() {
        ImportTierPricingService service = new ImportTierPricingService(tierRepository);

        assertThatThrownBy(() -> service.calculateCredits("IMPORT_ARCHIVE", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ImportPricingTier tier(
            String operationCode, String tierCode, Integer maxFiles, int credits, int sortOrder) {
        return ImportPricingTier.builder()
                .operationCode(operationCode)
                .tierCode(tierCode)
                .maxFiles(maxFiles)
                .credits(credits)
                .sortOrder(sortOrder)
                .build();
    }
}
