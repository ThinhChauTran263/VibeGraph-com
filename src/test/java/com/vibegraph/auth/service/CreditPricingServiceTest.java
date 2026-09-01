package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.entity.CreditPricingRule;
import com.vibegraph.auth.repository.CreditPricingRuleRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditPricingService")
class CreditPricingServiceTest {

    private static final long MIB = 1_048_576L;

    @Mock CreditPricingRuleRepository pricingRuleRepository;

    @ParameterizedTest(name = "{0} charges {6} credits")
    @MethodSource("pricingExamples")
    @DisplayName("uses base, file count, exact source MB, and one final ceiling")
    void calculateCredits_usesLiteralFormula(
            String operationCode,
            BigDecimal base,
            BigDecimal perFile,
            BigDecimal perMb,
            int fileCount,
            long sourceBytes,
            long expectedCharge) {
        CreditPricingService service = new CreditPricingService(pricingRuleRepository);
        when(pricingRuleRepository.findByOperationCode(operationCode))
                .thenReturn(Optional.of(rule(operationCode, base, perFile, perMb, true)));

        long charge = service.calculateCredits(operationCode, fileCount, sourceBytes);

        assertThat(charge).isEqualTo(expectedCharge);
    }

    static Stream<Arguments> pricingExamples() {
        return Stream.of(
                Arguments.of("MCP_TOOL_CALL", bd("1"), bd("0"), bd("0"), 0, 0L, 1L),
                Arguments.of("CLI_PUSH", bd("1"), bd("0.10"), bd("0"), 1, 0L, 2L),
                Arguments.of("PROJECT_ANALYZE", bd("5"), bd("0.01"), bd("1"), 3, 1L, 6L),
                Arguments.of("IMPORT_ARCHIVE", bd("3"), bd("0"), bd("1"), 0, 1L, 4L),
                Arguments.of("IMPORT_GITHUB", bd("3"), bd("0"), bd("1"), 0, MIB + MIB / 2, 5L));
    }

    @ParameterizedTest(name = "{0} bytes produce {1} credits")
    @MethodSource("byteBoundaries")
    @DisplayName("uses fractional source MB before applying the final ceiling")
    void calculateCredits_usesFractionalSourceMb(long sourceBytes, long expectedCharge) {
        CreditPricingService service = new CreditPricingService(pricingRuleRepository);
        when(pricingRuleRepository.findByOperationCode("BYTES"))
                .thenReturn(Optional.of(rule("BYTES", bd("0"), bd("0"), bd("1"), true)));

        assertThat(service.calculateCredits("BYTES", 0, sourceBytes)).isEqualTo(expectedCharge);
    }

    static Stream<Arguments> byteBoundaries() {
        return Stream.of(
                Arguments.of(0L, 0L),
                Arguments.of(1L, 1L),
                Arguments.of(MIB, 1L),
                Arguments.of(MIB + 1L, 2L),
                Arguments.of(MIB + MIB / 10, 2L));
    }

    @Test
    @DisplayName("fractional per-MB pricing is not inflated by pre-rounding source size")
    void calculateCredits_fractionalPerMb_usesLiteralFormula() {
        CreditPricingService service = new CreditPricingService(pricingRuleRepository);
        CreditPricingRule rule = rule("IMPORT_ARCHIVE", bd("0"), bd("0"), bd("1.5"), true);
        when(pricingRuleRepository.findByOperationCode("IMPORT_ARCHIVE"))
                .thenReturn(Optional.of(rule));

        assertThat(service.calculateCredits("IMPORT_ARCHIVE", 0, MIB + MIB / 10))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("maximum long byte input is calculated without arithmetic overflow")
    void calculateCredits_maximumSourceBytes_isSafe() {
        CreditPricingService service = new CreditPricingService(pricingRuleRepository);
        when(pricingRuleRepository.findByOperationCode("IMPORT_ARCHIVE"))
                .thenReturn(Optional.of(rule(
                        "IMPORT_ARCHIVE", bd("3"), bd("0"), bd("1"), true)));

        assertThat(service.calculateCredits("IMPORT_ARCHIVE", 0, Long.MAX_VALUE))
                .isEqualTo(8_796_093_022_211L);
    }

    @Test
    @DisplayName("fractional totals are rounded up instead of truncated")
    void calculateCredits_fractionalTotal_usesCeiling() {
        CreditPricingService service = new CreditPricingService(pricingRuleRepository);
        when(pricingRuleRepository.findByOperationCode("FRACTIONAL"))
                .thenReturn(Optional.of(rule("FRACTIONAL", bd("1.0001"), bd("0"), bd("0"), true)));

        assertThat(service.calculateCredits("FRACTIONAL", 0, 0)).isEqualTo(2L);
    }

    @Test
    @DisplayName("the minimum credits floor applies when the formula yields less")
    void calculateCredits_appliesMinimumFloor() {
        CreditPricingService service = new CreditPricingService(pricingRuleRepository);
        CreditPricingRule rule = CreditPricingRule.builder()
                .operationCode("IMPORT_ARCHIVE")
                .baseCredits(bd("2"))
                .perFileCredits(bd("0.01"))
                .perMbCredits(BigDecimal.ZERO)
                .minimumCredits(2)
                .isActive(true)
                .build();
        when(pricingRuleRepository.findByOperationCode("IMPORT_ARCHIVE"))
                .thenReturn(Optional.of(rule));

        // Zero files: formula gives the base (2) -> floor is met exactly.
        assertThat(service.calculateCredits("IMPORT_ARCHIVE", 0, 0)).isEqualTo(2L);
        // 300 files: 2 + 3 = 5 -> above the floor.
        assertThat(service.calculateCredits("IMPORT_ARCHIVE", 300, 0)).isEqualTo(5L);

        // A zero-priced rule with no floor stays free (deductCredits skips 0).
        when(pricingRuleRepository.findByOperationCode("FREE_OP"))
                .thenReturn(Optional.of(rule("FREE_OP", bd("0"), bd("0"), bd("0"), true)));
        assertThat(service.calculateCredits("FREE_OP", 10, 0)).isZero();
    }

    @Test
    @DisplayName("missing and inactive rules fail closed")
    void calculateCredits_missingOrInactiveRule_throws() {
        CreditPricingService service = new CreditPricingService(pricingRuleRepository);
        when(pricingRuleRepository.findByOperationCode("MISSING")).thenReturn(Optional.empty());
        when(pricingRuleRepository.findByOperationCode("INACTIVE"))
                .thenReturn(Optional.of(rule("INACTIVE", bd("1"), bd("0"), bd("0"), false)));

        assertThatThrownBy(() -> service.calculateCredits("MISSING", 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MISSING");
        assertThatThrownBy(() -> service.calculateCredits("INACTIVE", 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INACTIVE");
    }

    @Test
    @DisplayName("negative usage dimensions are rejected")
    void calculateCredits_negativeInputs_throws() {
        CreditPricingService service = new CreditPricingService(pricingRuleRepository);

        assertThatThrownBy(() -> service.calculateCredits("CLI_PUSH", -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculateCredits("IMPORT_ARCHIVE", 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CreditPricingRule rule(
            String operationCode,
            BigDecimal base,
            BigDecimal perFile,
            BigDecimal perMb,
            boolean active) {
        return CreditPricingRule.builder()
                .operationCode(operationCode)
                .baseCredits(base)
                .perFileCredits(perFile)
                .perMbCredits(perMb)
                .isActive(active)
                .build();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
