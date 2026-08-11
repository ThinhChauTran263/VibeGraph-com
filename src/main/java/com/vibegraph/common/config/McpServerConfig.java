package com.vibegraph.common.config;

import java.util.Arrays;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.CreditPricingService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.web.ApiKeyRequestContextAccessor;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.mcp.MeteredToolCallback;
import com.vibegraph.mcp.tool.ArchitectureTool;
import com.vibegraph.mcp.tool.ClassContextTool;
import com.vibegraph.mcp.tool.ExplainCompileErrorTool;
import com.vibegraph.mcp.tool.ExplainFailureTool;
import com.vibegraph.mcp.tool.FindReferencesTool;
import com.vibegraph.mcp.tool.FindRelatedTestsTool;
import com.vibegraph.mcp.tool.ImpactAnalysisTool;
import com.vibegraph.mcp.tool.LayerPatternTool;
import com.vibegraph.mcp.tool.ListProjectsTool;
import com.vibegraph.mcp.tool.MethodCpgTool;
import com.vibegraph.mcp.tool.MethodSourceTool;
import com.vibegraph.mcp.tool.PlanCodeChangeTool;
import com.vibegraph.mcp.tool.ProjectConventionsTool;
import com.vibegraph.mcp.tool.SearchSourceTool;
import com.vibegraph.mcp.tool.SourceFileTool;
import com.vibegraph.mcp.tool.SuggestTestPlanTool;
import com.vibegraph.mcp.tool.TraceEndpointTool;
import com.vibegraph.mcp.tool.VerifyChangeTool;

@Configuration
public class McpServerConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            ArchitectureTool architectureTool,
            ClassContextTool classContextTool,
            ImpactAnalysisTool impactAnalysisTool,
            LayerPatternTool layerPatternTool,
            SourceFileTool sourceFileTool,
            MethodSourceTool methodSourceTool,
            SearchSourceTool searchSourceTool,
            FindReferencesTool findReferencesTool,
            TraceEndpointTool traceEndpointTool,
            MethodCpgTool methodCpgTool,
            FindRelatedTestsTool findRelatedTestsTool,
            SuggestTestPlanTool suggestTestPlanTool,
            PlanCodeChangeTool planCodeChangeTool,
            ExplainFailureTool explainFailureTool,
            ProjectConventionsTool projectConventionsTool,
            ListProjectsTool listProjectsTool,
            VerifyChangeTool verifyChangeTool,
            ExplainCompileErrorTool explainCompileErrorTool,
            CurrentUser currentUser,
            CreditPricingService creditPricingService,
            CreditBalanceService creditBalanceService,
            ProjectOwnershipGuard ownershipGuard,
            FeatureGateService featureGateService,
            AccountAccessGuard accountAccessGuard,
            ApiKeyRequestContextAccessor apiKeyContextAccessor,
            ObjectMapper objectMapper) {
        ToolCallbackProvider baseProvider = MethodToolCallbackProvider.builder()
                .toolObjects(
                        architectureTool,
                        classContextTool,
                        impactAnalysisTool,
                        layerPatternTool,
                        sourceFileTool,
                        methodSourceTool,
                        searchSourceTool,
                        findReferencesTool,
                        traceEndpointTool,
                        methodCpgTool,
                        findRelatedTestsTool,
                        suggestTestPlanTool,
                        planCodeChangeTool,
                        explainFailureTool,
                        projectConventionsTool,
                        listProjectsTool,
                        verifyChangeTool,
                        explainCompileErrorTool)
                .build();
        return ToolCallbackProvider.from(Arrays.stream(baseProvider.getToolCallbacks())
                .map(callback -> new MeteredToolCallback(
                        callback,
                        currentUser,
                        creditPricingService,
                        creditBalanceService,
                        ownershipGuard,
                        featureGateService,
                        accountAccessGuard,
                        apiKeyContextAccessor,
                        objectMapper))
                .toList());
    }
}
