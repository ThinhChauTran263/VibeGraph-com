package com.vibegraph.auth.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AdminOverviewResponse;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminOverviewController")
class AdminOverviewControllerTest {

    private MockMvc mockMvc;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = Mockito.mock(AdminService.class);
        AdminOverviewController controller = new AdminOverviewController(adminService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/overview returns dashboard stats")
    void getOverview_succeeds() throws Exception {
        AdminOverviewResponse overview = new AdminOverviewResponse(
                100L,
                5L,
                20L,
                10L,
                3L,
                2L,
                Instant.now(),
                List.of(new AdminOverviewResponse.AdminSeriesPoint("2026-07", 4, "month")),
                List.of(new AdminOverviewResponse.AdminSeriesPoint("2026-07", 12, "month")),
                new AdminOverviewResponse.AdminStorageSummary(100L, 1000L, "projects", null),
                List.of(new AdminOverviewResponse.AdminDistributionPoint("FREE", 2)),
                List.of(),
                List.of(),
                List.of(new AdminOverviewResponse.AdminSecurityAlert(
                        "blocked-users", "ACCOUNT_BLOCK", "WARNING", "2 blocked", Instant.now())),
                List.of(new AdminOverviewResponse.AdminSeriesPoint(
                        "2026-07-17T13:05:00Z", 5L, "minute")));

        when(adminService.getOverview()).thenReturn(overview);

        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(100L))
                .andExpect(jsonPath("$.data.onlineUsers").value(5L))
                .andExpect(jsonPath("$.data.totalProjects").value(20L))
                .andExpect(jsonPath("$.data.openReports").value(3L))
                .andExpect(jsonPath("$.data.userGrowth[0].label").value("2026-07"))
                .andExpect(jsonPath("$.data.storage.sourceLabel").value("projects"))
                .andExpect(jsonPath("$.data.planDistribution[0].label").value("FREE"))
                .andExpect(jsonPath("$.data.securityAlerts[0].type").value("ACCOUNT_BLOCK"))
                .andExpect(jsonPath("$.data.onlineUserHistory[0].label")
                        .value("2026-07-17T13:05:00Z"))
                .andExpect(jsonPath("$.data.onlineUserHistory[0].value").value(5L));
    }
}
