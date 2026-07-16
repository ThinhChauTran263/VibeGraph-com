package com.vibegraph.auth.web;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AdminCreditAdjustmentRequest;
import com.vibegraph.auth.dto.AdminCreditOverviewResponse;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminCreditController")
class AdminCreditControllerTest {

    private MockMvc mockMvc;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = Mockito.mock(AdminService.class);
        AdminCreditController controller = new AdminCreditController(adminService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/credits/users/{userId} returns credit overview")
    void getOverview_succeeds() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminCreditOverviewResponse creditResponse = new AdminCreditOverviewResponse(
                userId, 100, 20, 10, 90, Collections.emptyList());

        when(adminService.getCreditOverview(userId)).thenReturn(creditResponse);

        mockMvc.perform(get("/api/admin/credits/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.creditBalance").value(90));
    }

    @Test
    @DisplayName("POST /api/admin/credits/users/{userId}/adjust records adjustment")
    void adjustCredits_succeeds() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/credits/users/" + userId + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsDelta\":15,\"reason\":\"Bonus credits\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminService).adjustCredits(any(UUID.class), any(AdminCreditAdjustmentRequest.class));
    }
}
