package com.vibegraph.auth.web;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AdminPlanResponse;
import com.vibegraph.auth.service.AdminPlanManagementService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminPlanController")
class AdminPlanControllerTest {

    private MockMvc mockMvc;
    private AdminPlanManagementService planManagementService;

    @BeforeEach
    void setUp() {
        planManagementService = Mockito.mock(AdminPlanManagementService.class);
        AdminPlanController controller = new AdminPlanController(planManagementService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/plans returns plans catalog")
    void getPlans_succeeds() throws Exception {
        AdminPlanResponse plan = new AdminPlanResponse(
                "PRO", "Pro", 500000L, 10, 500, false);

        when(planManagementService.list()).thenReturn(Collections.singletonList(plan));

        mockMvc.perform(get("/api/admin/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("PRO"))
                .andExpect(jsonPath("$.data[0].name").value("Pro"))
                .andExpect(jsonPath("$.data[0].monthlyCreditLimit").value(500))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
    }
}
