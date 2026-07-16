package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.repository.FeatureFlagRepository;
import com.vibegraph.common.exception.FeatureDisabledException;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureGateService")
class FeatureGateServiceTest {

    @Mock FeatureFlagRepository featureFlagRepository;

    @Test
    @DisplayName("missing or enabled flags allow the feature by default")
    void assertEnabled_allowsWhenNoDisabledFlagExists() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);
        when(featureFlagRepository.existsByKeyAndEnabledFalse("global.cli_push")).thenReturn(false);

        service.assertEnabled("global.cli_push");

        verify(featureFlagRepository).existsByKeyAndEnabledFalse("global.cli_push");
    }

    @Test
    @DisplayName("disabled flag blocks the feature with a stable exception")
    void assertEnabled_disabledFlagThrows() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);
        when(featureFlagRepository.existsByKeyAndEnabledFalse("global.cli_push")).thenReturn(true);

        assertThatThrownBy(() -> service.assertEnabled("global.cli_push"))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("global.cli_push");
    }

    @Test
    @DisplayName("MCP tool checks global MCP and normalized per-tool flag")
    void assertMcpToolEnabled_checksGlobalAndToolFlag() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);

        service.assertMcpToolEnabled("Plan Code Change");

        verify(featureFlagRepository).existsByKeyAndEnabledFalse(FeatureGateService.GLOBAL_MCP);
        verify(featureFlagRepository).existsByKeyAndEnabledFalse("mcp.tool.plan_code_change");
    }

    @Test
    @DisplayName("disabled global MCP blocks before the per-tool lookup")
    void assertMcpToolEnabled_globalDisabledShortCircuits() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);
        when(featureFlagRepository.existsByKeyAndEnabledFalse(FeatureGateService.GLOBAL_MCP)).thenReturn(true);

        assertThatThrownBy(() -> service.assertMcpToolEnabled("source_file"))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining(FeatureGateService.GLOBAL_MCP);

        verify(featureFlagRepository, never()).existsByKeyAndEnabledFalse("mcp.tool.source_file");
    }
}
