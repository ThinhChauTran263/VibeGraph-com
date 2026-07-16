package com.vibegraph.mcp;

import java.util.UUID;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.CreditPricingService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;

public final class MeteredToolCallback implements ToolCallback {

    static final String OPERATION_CODE = "MCP_TOOL_CALL";

    private final ToolCallback delegate;
    private final CurrentUser currentUser;
    private final CreditPricingService creditPricingService;
    private final CreditBalanceService creditBalanceService;
    private final ProjectOwnershipGuard ownershipGuard;
    private final FeatureGateService featureGateService;
    private final AccountAccessGuard accountAccessGuard;
    private final ObjectMapper objectMapper;

    public MeteredToolCallback(
            ToolCallback delegate,
            CurrentUser currentUser,
            CreditPricingService creditPricingService,
            CreditBalanceService creditBalanceService,
            ProjectOwnershipGuard ownershipGuard,
            FeatureGateService featureGateService,
            AccountAccessGuard accountAccessGuard,
            ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.currentUser = currentUser;
        this.creditPricingService = creditPricingService;
        this.creditBalanceService = creditBalanceService;
        this.ownershipGuard = ownershipGuard;
        this.featureGateService = featureGateService;
        this.accountAccessGuard = accountAccessGuard;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return meter(toolInput, () -> delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        return meter(toolInput, () -> delegate.call(toolInput, toolContext));
    }

    private String meter(String toolInput, ToolInvocation invocation) {
        UUID userId = currentUser.id();
        accountAccessGuard.assertProductAccess(userId);
        featureGateService.assertMcpToolEnabled(delegate.getToolDefinition().name());
        String projectId = extractProjectId(toolInput);
        if (projectId != null) {
            ownershipGuard.assertOwner(projectId, userId);
        }

        long requiredCredits = creditPricingService.calculateCredits(OPERATION_CODE, 0, 0);
        creditBalanceService.deductCredits(
                userId, requiredCredits, "MCP", OPERATION_CODE, projectId);
        return invocation.call();
    }

    private String extractProjectId(String toolInput) {
        if (!declaresProjectId()) {
            return null;
        }

        JsonNode input = readJson(toolInput, "MCP tool input must be valid JSON");
        JsonNode projectId = input.get("projectId");
        if (projectId == null || !projectId.isTextual() || projectId.textValue().isBlank()) {
            throw new IllegalArgumentException("projectId must be a non-blank string");
        }
        return projectId.textValue();
    }

    private boolean declaresProjectId() {
        JsonNode schema = readJson(
                delegate.getToolDefinition().inputSchema(),
                "MCP tool input schema must be valid JSON");
        return schema.path("properties").has("projectId");
    }

    private JsonNode readJson(String json, String message) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new IllegalStateException(message, ex);
        }
    }

    @FunctionalInterface
    private interface ToolInvocation {
        String call();
    }
}
