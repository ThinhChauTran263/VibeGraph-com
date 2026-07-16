package com.vibegraph.auth.web;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.SecurityEventResponse;
import com.vibegraph.auth.service.AdminSecurityMonitorService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminSecurityMonitorController")
class AdminSecurityMonitorControllerTest {

    private MockMvc mockMvc;
    private AdminSecurityMonitorService securityMonitorService;

    @BeforeEach
    void setUp() {
        securityMonitorService = Mockito.mock(AdminSecurityMonitorService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminSecurityMonitorController(securityMonitorService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/security/events returns sanitized event refs")
    void events_succeeds() throws Exception {
        when(securityMonitorService.recentEvents(25)).thenReturn(List.of(new SecurityEventResponse(
                UUID.randomUUID(), "SUSPICIOUS_API_KEY", "WARNING", null, "vgk_abcd...", "API", "Burst detected", null)));

        mockMvc.perform(get("/api/admin/security/events?limit=25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].apiKeyRef").value("vgk_abcd..."));
    }
}
