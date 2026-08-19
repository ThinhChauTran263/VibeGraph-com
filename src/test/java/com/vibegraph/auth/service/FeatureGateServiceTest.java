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
    @DisplayName("Phase 7 feature flag constants use canonical backend keys")
    void canonicalFeatureKeys_matchPhase7Contract() {
        org.assertj.core.api.Assertions.assertThat(FeatureGateService.REGISTRATION).isEqualTo("registration");
        org.assertj.core.api.Assertions.assertThat(FeatureGateService.API_KEYS_CREATE_GLOBAL)
                .isEqualTo("api_keys.create.global");
        org.assertj.core.api.Assertions.assertThat(FeatureGateService.CLI_PUSH).isEqualTo("cli.push");
        org.assertj.core.api.Assertions.assertThat(FeatureGateService.IMPORT_ARCHIVE).isEqualTo("import.archive");
        org.assertj.core.api.Assertions.assertThat(FeatureGateService.IMPORT_GITHUB).isEqualTo("import.github");
        org.assertj.core.api.Assertions.assertThat(FeatureGateService.PROJECT_ANALYZE).isEqualTo("project.analyze");
        org.assertj.core.api.Assertions.assertThat(FeatureGateService.MCP_ENABLED).isEqualTo("mcp.enabled");
        org.assertj.core.api.Assertions.assertThat(FeatureGateService.USECASE_GENERATE).isEqualTo("usecase.generate");
    }

    @Test
    @DisplayName("missing or enabled flags allow the feature by default")
    void assertEnabled_allowsWhenNoDisabledFlagExists() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);
        when(featureFlagRepository.existsByKeyAndEnabledFalse(FeatureGateService.CLI_PUSH)).thenReturn(false);

        service.assertEnabled(FeatureGateService.CLI_PUSH);

        verify(featureFlagRepository).existsByKeyAndEnabledFalse(FeatureGateService.CLI_PUSH);
    }

    @Test
    @DisplayName("disabled flag blocks the feature with a stable exception")
    void assertEnabled_disabledFlagThrows() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);
        when(featureFlagRepository.existsByKeyAndEnabledFalse(FeatureGateService.CLI_PUSH)).thenReturn(true);

        assertThatThrownBy(() -> service.assertEnabled(FeatureGateService.CLI_PUSH))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining(FeatureGateService.CLI_PUSH);
    }
    @Test
    @DisplayName("MCP tool checks global MCP and normalized per-tool flag")
    void assertMcpToolEnabled_checksGlobalAndToolFlag() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);

        service.assertMcpToolEnabled("Plan Code Change");

        verify(featureFlagRepository).existsByKeyAndEnabledFalse("mcp.enabled");
        verify(featureFlagRepository).existsByKeyAndEnabledFalse("mcp.tool.plan_code_change");
    }

    @Test
    @DisplayName("disabled global MCP blocks before the per-tool lookup")
    void assertMcpToolEnabled_globalDisabledShortCircuits() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);
        when(featureFlagRepository.existsByKeyAndEnabledFalse("mcp.enabled")).thenReturn(true);

        assertThatThrownBy(() -> service.assertMcpToolEnabled("source_file"))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("mcp.enabled");

        verify(featureFlagRepository, never()).existsByKeyAndEnabledFalse("mcp.tool.source_file");
    }

    @Test
    @DisplayName("capabilities expose canonical global and persisted MCP child flags")
    void capabilities_returnsSafeEffectiveMap() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);
        var child = com.vibegraph.auth.domain.FeatureFlag.builder()
                .key("mcp.tool.source_file")
                .scope("MCP_TOOL")
                .displayName("Source file")
                .enabled(false)
                .description("admin-only description")
                .build();
        when(featureFlagRepository.findAll()).thenReturn(java.util.List.of(child));

        var capabilities = service.capabilities();

        org.assertj.core.api.Assertions.assertThat(capabilities)
                .containsKeys(FeatureGateService.IMPORT_ARCHIVE, FeatureGateService.MCP_ENABLED,
                        "mcp.tool.source_file");
        org.assertj.core.api.Assertions.assertThat(capabilities.get("mcp.tool.source_file").enabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(capabilities.toString()).doesNotContain("admin-only description");
    }

    @Test
    @DisplayName("disabled MCP global flag makes child capabilities fail closed")
    void capabilities_globalMcpDisabled_disablesChildren() {
        FeatureGateService service = new FeatureGateService(featureFlagRepository);
        var child = com.vibegraph.auth.domain.FeatureFlag.builder()
                .key("mcp.tool.source_file")
                .scope("MCP_TOOL")
                .displayName("Source file")
                .enabled(true)
                .build();
        var global = com.vibegraph.auth.domain.FeatureFlag.builder()
                .key(FeatureGateService.MCP_ENABLED)
                .scope("GLOBAL")
                .displayName("MCP")
                .enabled(false)
                .build();
        when(featureFlagRepository.findAll()).thenReturn(java.util.List.of(global, child));

        var capabilities = service.capabilities();

        org.assertj.core.api.Assertions.assertThat(capabilities.get(FeatureGateService.MCP_ENABLED).enabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(capabilities.get("mcp.tool.source_file").enabled()).isFalse();
    }
}
