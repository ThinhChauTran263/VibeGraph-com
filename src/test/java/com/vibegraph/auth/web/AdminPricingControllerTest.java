package com.vibegraph.auth.web;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.domain.CreditPricingRule;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminPricingController")
class AdminPricingControllerTest {

    private MockMvc mockMvc;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = Mockito.mock(AdminService.class);
        AdminPricingController controller = new AdminPricingController(adminService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/pricing-rules returns rules list")
    void getPricingRules_succeeds() throws Exception {
        CreditPricingRule rule = CreditPricingRule.builder()
                .operationCode("MCP_CALL")
                .displayName("MCP Tool Call")
                .baseCredits(java.math.BigDecimal.valueOf(2))
                .build();

        when(adminService.getPricingRules()).thenReturn(Collections.singletonList(rule));

        mockMvc.perform(get("/api/admin/pricing-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].operationCode").value("MCP_CALL"))
                .andExpect(jsonPath("$.data[0].baseCredits").value(2));
    }
}
