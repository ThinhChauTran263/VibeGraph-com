package com.vibegraph.auth.web;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AdminPricingRuleResponse;
import com.vibegraph.auth.service.AdminPricingManagementService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminPricingController")
class AdminPricingControllerTest {

    private MockMvc mockMvc;
    private AdminPricingManagementService pricingManagementService;

    @BeforeEach
    void setUp() {
        pricingManagementService = Mockito.mock(AdminPricingManagementService.class);
        AdminPricingController controller = new AdminPricingController(pricingManagementService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/pricing-rules returns rules list")
    void getPricingRules_succeeds() throws Exception {
        AdminPricingRuleResponse rule = new AdminPricingRuleResponse(
                "MCP_CALL",
                "MCP Tool Call",
                java.math.BigDecimal.valueOf(2),
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                1,
                true);

        when(pricingManagementService.list()).thenReturn(Collections.singletonList(rule));

        mockMvc.perform(get("/api/admin/pricing-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].operationCode").value("MCP_CALL"))
                .andExpect(jsonPath("$.data[0].baseCredits").value(2))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
    }
}
