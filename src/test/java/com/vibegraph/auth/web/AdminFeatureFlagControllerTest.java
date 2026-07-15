package com.vibegraph.auth.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.FeatureFlagResponse;
import com.vibegraph.auth.service.AdminFeatureFlagService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminFeatureFlagController")
class AdminFeatureFlagControllerTest {

    private MockMvc mockMvc;
    private AdminFeatureFlagService featureFlagService;

    @BeforeEach
    void setUp() {
        featureFlagService = Mockito.mock(AdminFeatureFlagService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminFeatureFlagController(featureFlagService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/admin/feature-flags creates an MCP tool flag")
    void create_mcpToolFlag_succeeds() throws Exception {
        when(featureFlagService.create(any()))
                .thenReturn(new FeatureFlagResponse("mcp.tool.plan_code_change", "MCP_TOOL", "Plan code change", false, null, null));

        mockMvc.perform(post("/api/admin/feature-flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"mcp.tool.plan_code_change","scope":"MCP_TOOL","displayName":"Plan code change","enabled":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scope").value("MCP_TOOL"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(featureFlagService).create(any());
    }
}
