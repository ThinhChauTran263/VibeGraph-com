package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.FeatureFlag;
import com.vibegraph.auth.dto.FeatureFlagRequest;
import com.vibegraph.auth.repository.FeatureFlagRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFeatureFlagService")
class AdminFeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private AuditService auditService;

    private AdminFeatureFlagService service;

    @BeforeEach
    void setUp() {
        service = new AdminFeatureFlagService(featureFlagRepository, auditService);
    }

    @Test
    @DisplayName("rejects legacy global key before persistence")
    void createRejectsLegacyKeyBeforePersistence() {
        FeatureFlagRequest request = new FeatureFlagRequest("global.cli_push", "GLOBAL", "CLI Push", false, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported feature flag key");

        verify(featureFlagRepository, never()).save(any(FeatureFlag.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("rejects unknown global key before persistence")
    void createRejectsUnknownGlobalKeyBeforePersistence() {
        FeatureFlagRequest request = new FeatureFlagRequest("billing.payments", "GLOBAL", "Payments", false, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported feature flag key");

        verify(featureFlagRepository, never()).save(any(FeatureFlag.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("rejects MCP child key with GLOBAL scope")
    void createRejectsMcpChildWithGlobalScope() {
        FeatureFlagRequest request = new FeatureFlagRequest(
                "mcp.tool.plan_code_change", "GLOBAL", "Plan code change", false, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MCP tool");

        verify(featureFlagRepository, never()).save(any(FeatureFlag.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("accepts canonical MCP child key")
    void createAcceptsCanonicalMcpChildKey() {
        FeatureFlagRequest request = new FeatureFlagRequest(
                "mcp.tool.plan_code_change", "MCP_TOOL", "Plan code change", false, null);
        when(featureFlagRepository.existsByKey(request.key())).thenReturn(false);
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        verify(featureFlagRepository).save(any(FeatureFlag.class));
    }
}
