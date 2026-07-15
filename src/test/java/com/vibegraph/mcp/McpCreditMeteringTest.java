package com.vibegraph.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.CreditPricingService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.common.exception.FeatureDisabledException;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.InsufficientCreditsException;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;

@ExtendWith(MockitoExtension.class)
@DisplayName("MCP credit metering and project authorization")
class McpCreditMeteringTest {

    private static final ToolDefinition PROJECT_TOOL = DefaultToolDefinition.builder()
            .name("project_tool")
            .description("Project-scoped test tool")
            .inputSchema("""
                    {"type":"object","properties":{"projectId":{"type":"string"}},"required":["projectId"]}
                    """)
            .build();

    private static final ToolDefinition NON_PROJECT_TOOL = DefaultToolDefinition.builder()
            .name("non_project_tool")
            .description("Non-project test tool")
            .inputSchema("{" + "\"type\":\"object\",\"properties\":{}}")
            .build();

    @Mock ToolCallback delegate;
    @Mock CurrentUser currentUser;
    @Mock CreditPricingService creditPricingService;
    @Mock CreditBalanceService creditBalanceService;
    @Mock ProjectOwnershipGuard ownershipGuard;
    @Mock FeatureGateService featureGateService;
    @Mock AccountSettingsService accountSettingsService;

    private UUID userId;
    private MeteredToolCallback callback;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(currentUser.id()).thenReturn(userId);
        org.mockito.Mockito.lenient().when(delegate.getToolDefinition()).thenReturn(NON_PROJECT_TOOL);
        callback = new MeteredToolCallback(
                delegate,
                currentUser,
                creditPricingService,
                creditBalanceService,
                ownershipGuard,
                featureGateService,
                accountSettingsService,
                new ObjectMapper());
    }

    @Test
    @DisplayName("owned project call authorizes before metering and attributes the debit")
    void projectCall_owner_authorizesBeforeMetering() {
        when(delegate.getToolDefinition()).thenReturn(PROJECT_TOOL);
        when(currentUser.id()).thenReturn(userId);
        when(creditPricingService.calculateCredits("MCP_TOOL_CALL", 0, 0)).thenReturn(2L);
        when(delegate.call("{\"projectId\":\"p1\"}")).thenReturn("result");

        String result = callback.call("{\"projectId\":\"p1\"}");

        assertThat(result).isEqualTo("result");
        InOrder order = inOrder(
                currentUser, ownershipGuard, creditPricingService, creditBalanceService, delegate);
        order.verify(currentUser).id();
        order.verify(ownershipGuard).assertOwner("p1", userId);
        order.verify(creditPricingService).calculateCredits("MCP_TOOL_CALL", 0, 0);
        order.verify(creditBalanceService).assertCreditsAvailable(userId, 2L);
        order.verify(delegate).call("{\"projectId\":\"p1\"}");
        order.verify(creditBalanceService)
                .deductCredits(userId, 2L, "MCP", "MCP_TOOL_CALL", "p1");
        verify(featureGateService).assertMcpToolEnabled("project_tool");
    }

    @Test
    @DisplayName("blocked account is rejected before MCP feature, ownership, metering, or delegated work")
    void blockedAccount_blocksBeforeAnyWork() {
        when(currentUser.id()).thenReturn(userId);
        doThrow(new com.vibegraph.common.exception.AccountBlockedException(
                        "internal reason", "Policy review"))
                .when(accountSettingsService).assertNotBlocked(userId);

        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(com.vibegraph.common.exception.AccountBlockedException.class)
                .hasMessage("internal reason");

        verify(currentUser).id();
        verify(accountSettingsService).assertNotBlocked(userId);
        verifyNoInteractions(featureGateService, ownershipGuard, creditPricingService, creditBalanceService);
        verify(delegate, never()).call("{}");
    }

    @Test
    @DisplayName("disabled MCP tool is blocked before ownership, metering, or delegated work")
    void featureFlagDisabled_blocksBeforeAnyWork() {
        doThrow(new FeatureDisabledException("mcp.tool.non_project_tool"))
                .when(featureGateService).assertMcpToolEnabled("non_project_tool");

        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(FeatureDisabledException.class);

        verify(currentUser).id();
        verify(accountSettingsService).assertNotBlocked(userId);
        verifyNoInteractions(ownershipGuard, creditPricingService, creditBalanceService);
        verify(delegate, never()).call("{}");
    }

    @Test
    @DisplayName("wrong project owner is forbidden before metering or delegated work")
    void projectCall_wrongOwner_blocksMeteringAndDelegate() {
        when(delegate.getToolDefinition()).thenReturn(PROJECT_TOOL);
        when(currentUser.id()).thenReturn(userId);
        doThrow(new ForbiddenException("Access denied"))
                .when(ownershipGuard).assertOwner("p1", userId);

        assertThatThrownBy(() -> callback.call("{\"projectId\":\"p1\"}"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");

        verifyNoInteractions(creditPricingService, creditBalanceService);
        verify(delegate, never()).call("{\"projectId\":\"p1\"}");
    }

    @Test
    @DisplayName("missing ownership row returns project-not-found before metering or work")
    void projectCall_missingOwnership_blocksMeteringAndDelegate() {
        when(delegate.getToolDefinition()).thenReturn(PROJECT_TOOL);
        when(currentUser.id()).thenReturn(userId);
        doThrow(new ProjectNotFoundException("Project not found: missing"))
                .when(ownershipGuard).assertOwner("missing", userId);

        assertThatThrownBy(() -> callback.call("{\"projectId\":\"missing\"}"))
                .isInstanceOf(ProjectNotFoundException.class);

        verifyNoInteractions(creditPricingService, creditBalanceService);
        verify(delegate, never()).call("{\"projectId\":\"missing\"}");
    }

    @Test
    @DisplayName("project-scoped calls reject missing, blank, and non-text project IDs")
    void projectCall_invalidProjectId_blocksMeteringAndDelegate() {
        when(delegate.getToolDefinition()).thenReturn(PROJECT_TOOL);

        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId");
        assertThatThrownBy(() -> callback.call("{\"projectId\":\"  \"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId");
        assertThatThrownBy(() -> callback.call("{\"projectId\":42}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId");

        verify(currentUser, org.mockito.Mockito.times(3)).id();
        verify(accountSettingsService, org.mockito.Mockito.times(3)).assertNotBlocked(userId);
        verify(featureGateService, org.mockito.Mockito.times(3)).assertMcpToolEnabled("project_tool");
        verifyNoInteractions(ownershipGuard, creditPricingService, creditBalanceService);
    }

    @Test
    @DisplayName("context-aware owned project call preserves ToolContext and meters once")
    void contextAwareProjectCall_owner_preservesContextAndMetersOnce() {
        ToolContext context = new ToolContext(Map.of("requestId", "r1"));
        when(delegate.getToolDefinition()).thenReturn(PROJECT_TOOL);
        when(currentUser.id()).thenReturn(userId);
        when(creditPricingService.calculateCredits("MCP_TOOL_CALL", 0, 0)).thenReturn(1L);
        when(delegate.call("{\"projectId\":\"p1\"}", context)).thenReturn("context-result");

        assertThat(callback.call("{\"projectId\":\"p1\"}", context))
                .isEqualTo("context-result");

        verify(ownershipGuard).assertOwner("p1", userId);
        verify(delegate).call("{\"projectId\":\"p1\"}", context);
        verify(creditBalanceService)
                .deductCredits(userId, 1L, "MCP", "MCP_TOOL_CALL", "p1");
    }

    @Test
    @DisplayName("non-project MCP tool skips ownership but is still metered")
    void nonProjectCall_skipsOwnershipAndMeters() {
        when(currentUser.id()).thenReturn(userId);
        when(creditPricingService.calculateCredits("MCP_TOOL_CALL", 0, 0)).thenReturn(2L);
        when(delegate.call("{}")).thenReturn("result");

        assertThat(callback.call("{}")).isEqualTo("result");

        verifyNoInteractions(ownershipGuard);
        verify(creditBalanceService)
                .deductCredits(userId, 2L, "MCP", "MCP_TOOL_CALL", null);
    }

    @Test
    @DisplayName("insufficient credits prevent the delegated MCP tool from running")
    void insufficientCredits_blocksDelegate() {
        when(currentUser.id()).thenReturn(userId);
        when(creditPricingService.calculateCredits("MCP_TOOL_CALL", 0, 0)).thenReturn(3L);
        doThrow(new InsufficientCreditsException("Insufficient credits"))
                .when(creditBalanceService).assertCreditsAvailable(userId, 3L);

        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(InsufficientCreditsException.class);

        verify(delegate, never()).call("{}");
        verify(creditBalanceService, never())
                .deductCredits(userId, 3L, "MCP", "MCP_TOOL_CALL", null);
    }

    @Test
    @DisplayName("failed MCP tools are not deducted")
    void failedDelegate_isNotDeducted() {
        when(currentUser.id()).thenReturn(userId);
        when(creditPricingService.calculateCredits("MCP_TOOL_CALL", 0, 0)).thenReturn(1L);
        when(delegate.call("{}")).thenThrow(new IllegalStateException("tool failed"));

        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tool failed");

        verify(creditBalanceService, never())
                .deductCredits(userId, 1L, "MCP", "MCP_TOOL_CALL", null);
    }

    @Test
    @DisplayName("the decorator preserves tool definition and metadata identity")
    void definitionAndMetadata_areTransparent() {
        ToolMetadata metadata = ToolMetadata.builder().returnDirect(true).build();
        when(delegate.getToolMetadata()).thenReturn(metadata);

        assertThat(callback.getToolDefinition()).isSameAs(NON_PROJECT_TOOL);
        assertThat(callback.getToolMetadata()).isSameAs(metadata);
    }
}
