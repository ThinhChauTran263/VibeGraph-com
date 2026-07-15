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

import com.vibegraph.auth.domain.CreditPricingRule;
import com.vibegraph.auth.repository.CreditPricingRuleRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditPricingService")
class CreditPricingServiceTest {

    private static final long MIB = 1_048_576L;

    @Mock CreditPricingRuleRepository pricingRuleRepository;

    @ParameterizedTest(name = "{0} charges {6} credits")
    @MethodSource("pricingExamples")
    @DisplayName("uses base, file count, rounded-up source MB, and final ceiling")
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
                Arguments.of("PROJECT_ANALYZE", bd("5"), bd("0.01"), bd("1"), 3, 1L, 7L),
                Arguments.of("IMPORT_ARCHIVE", bd("3"), bd("0"), bd("1"), 0, 1L, 4L),
                Arguments.of("IMPORT_GITHUB", bd("3"), bd("0"), bd("1"), 0, MIB + MIB / 2, 5L));
    }

    @ParameterizedTest(name = "{0} bytes count as {1} MB credits")
    @MethodSource("byteBoundaries")
    @DisplayName("rounds source bytes up to whole MiB")
    void calculateCredits_roundsBytesUp(long sourceBytes, long expectedCharge) {
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
                Arguments.of(MIB + 1L, 2L));
    }

    @Test
    @DisplayName("node-aware pricing rounds node units up and enforces the minimum")
    void calculateCredits_nodeAware_usesNodesAndMinimum() {
        CreditPricingService service = new CreditPricingService(pricingRuleRepository);
        CreditPricingRule rule = rule("PROJECT_ANALYZE", bd("1"), bd("0"), bd("0"), true);
        rule.setPer1kNodesCredits(bd("2"));
        rule.setMinimumCredits(5);
        when(pricingRuleRepository.findByOperationCode("PROJECT_ANALYZE"))
                .thenReturn(Optional.of(rule));

        assertThat(service.calculateCredits("PROJECT_ANALYZE", 0, 0, 1)).isEqualTo(5L);
        assertThat(service.calculateCredits("PROJECT_ANALYZE", 0, 0, 2_001)).isEqualTo(7L);
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
                .per1kNodesCredits(bd("999"))
                .minimumCredits(999)
                .isActive(active)
                .build();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
