package com.vibegraph.mcp;

import java.util.UUID;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vibegraph.auth.web.ApiKeyRequestContext;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.CreditPricingService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.web.ApiKeyRequestContextAccessor;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.mcp.orchestration.McpTaskExecutionCoordinator;

public final class MeteredToolCallback implements ToolCallback {

    static final String OPERATION_CODE = "MCP_TOOL_CALL";

    private final ToolCallback delegate;
    private final CurrentUser currentUser;
    private final CreditPricingService creditPricingService;
    private final CreditBalanceService creditBalanceService;
    private final ProjectOwnershipGuard ownershipGuard;
    private final FeatureGateService featureGateService;
    private final AccountAccessGuard accountAccessGuard;
    private final ApiKeyRequestContextAccessor apiKeyContextAccessor;
    private final ObjectMapper objectMapper;
    private final McpTaskExecutionCoordinator taskCoordinator;
    /** Whether the delegate's input schema declares projectId — immutable per tool, computed once. */
    private final boolean declaresProjectId;

    public MeteredToolCallback(
            ToolCallback delegate,
            CurrentUser currentUser,
            CreditPricingService creditPricingService,
            CreditBalanceService creditBalanceService,
            ProjectOwnershipGuard ownershipGuard,
            FeatureGateService featureGateService,
            AccountAccessGuard accountAccessGuard,
            ApiKeyRequestContextAccessor apiKeyContextAccessor,
            ObjectMapper objectMapper) {
        this(delegate, currentUser, creditPricingService, creditBalanceService, ownershipGuard,
                featureGateService, accountAccessGuard, apiKeyContextAccessor, objectMapper, null);
    }

    public MeteredToolCallback(
            ToolCallback delegate,
            CurrentUser currentUser,
            CreditPricingService creditPricingService,
            CreditBalanceService creditBalanceService,
            ProjectOwnershipGuard ownershipGuard,
            FeatureGateService featureGateService,
            AccountAccessGuard accountAccessGuard,
            ApiKeyRequestContextAccessor apiKeyContextAccessor,
            ObjectMapper objectMapper,
            McpTaskExecutionCoordinator taskCoordinator) {
        this.delegate = delegate;
        this.currentUser = currentUser;
        this.creditPricingService = creditPricingService;
        this.creditBalanceService = creditBalanceService;
        this.ownershipGuard = ownershipGuard;
        this.featureGateService = featureGateService;
        this.accountAccessGuard = accountAccessGuard;
        this.apiKeyContextAccessor = apiKeyContextAccessor;
        this.objectMapper = objectMapper;
        this.taskCoordinator = taskCoordinator;
        this.declaresProjectId = computeDeclaresProjectId();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        if (!declaresProjectId) {
            return delegate.getToolDefinition();
        }
        JsonNode schema = readJson(delegate.getToolDefinition().inputSchema(),
                "MCP tool input schema must be valid JSON");
        if (!(schema instanceof ObjectNode objectSchema)) {
            return delegate.getToolDefinition();
        }
        JsonNode required = objectSchema.get("required");
        if (!(required instanceof ArrayNode requiredFields) || !requiredFields.toString().contains("projectId")) {
            return delegate.getToolDefinition();
        }
        ObjectNode copy = objectSchema.deepCopy();
        ArrayNode optionalRequired = copy.putArray("required");
        requiredFields.forEach(field -> {
            if (!"projectId".equals(field.asText())) {
                optionalRequired.add(field);
            }
        });
        return DefaultToolDefinition.builder()
                .name(delegate.getToolDefinition().name())
                .description(delegate.getToolDefinition().description())
                .inputSchema(copy.toString())
                .build();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return meter(toolInput, effectiveInput -> delegate.call(effectiveInput));
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        return meter(toolInput, effectiveInput -> delegate.call(effectiveInput, toolContext));
    }

    private String meter(String toolInput, ToolInvocation invocation) {
        UUID userId = currentUser.id();
        accountAccessGuard.assertProductAccess(userId);
        featureGateService.assertMcpToolEnabled(delegate.getToolDefinition().name());
        String effectiveInput = enrichProjectInput(toolInput);
        String projectId = extractProjectId(effectiveInput);
        if (projectId != null) {
            apiKeyContextAccessor.assertProjectMatches(projectId);
            ownershipGuard.assertOwner(projectId, userId);
        }

        long requiredCredits = creditPricingService.calculateCredits(OPERATION_CODE, 0, 0);
        creditBalanceService.deductCredits(
                userId, requiredCredits, "MCP", OPERATION_CODE, projectId);
        if (taskCoordinator == null) {
            return invocation.call(effectiveInput);
        }
        return taskCoordinator.execute(delegate.getToolDefinition().name(), projectId,
                () -> invocation.call(effectiveInput));
    }

    private String extractProjectId(String toolInput) {
        if (!declaresProjectId) {
            return null;
        }

        JsonNode input = readJson(toolInput, "MCP tool input must be valid JSON");
        JsonNode projectId = input.get("projectId");
        if (projectId == null) {
            return apiKeyContextAccessor.current()
                    .map(ApiKeyRequestContext::projectId)
                    .filter(id -> id != null && !id.isBlank())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "projectId is required when no project-bound API key is present"));
        }
        if (!projectId.isTextual() || projectId.textValue().isBlank()) {
            throw new IllegalArgumentException("projectId must be a non-blank string");
        }
        return projectId.textValue();
    }

    private String enrichProjectInput(String toolInput) {
        if (!declaresProjectId) {
            return toolInput;
        }
        JsonNode input = readJson(toolInput, "MCP tool input must be valid JSON");
        if (input.has("projectId")) {
            return toolInput;
        }
        String projectId = extractProjectId(toolInput);
        if (!(input instanceof ObjectNode objectInput)) {
            throw new IllegalArgumentException("MCP tool input must be a JSON object");
        }
        objectInput.put("projectId", projectId);
        try {
            return objectMapper.writeValueAsString(objectInput);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("MCP tool input could not be normalized", ex);
        }
    }

    private boolean computeDeclaresProjectId() {
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
        String call(String effectiveInput);
    }
}
