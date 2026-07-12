package com.vibegraph.auth.web;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminPlanController")
class AdminPlanControllerTest {

    private MockMvc mockMvc;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = Mockito.mock(AdminService.class);
        AdminPlanController controller = new AdminPlanController(adminService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/plans returns plans catalog")
    void getPlans_succeeds() throws Exception {
        Plan plan = Plan.builder()
                .code("PRO")
                .name("Pro")
                .storageLimitBytes(500000L)
                .monthlyCreditLimit(500)
                .build();

        when(adminService.getPlans()).thenReturn(Collections.singletonList(plan));

        mockMvc.perform(get("/api/admin/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("PRO"))
                .andExpect(jsonPath("$.data[0].name").value("Pro"))
                .andExpect(jsonPath("$.data[0].monthlyCreditLimit").value(500));
    }
}
