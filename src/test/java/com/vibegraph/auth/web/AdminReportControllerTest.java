package com.vibegraph.auth.web;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.dto.AdminFeedbackDetailResponse;
import com.vibegraph.auth.dto.AdminFeedbackReplyRequest;
import com.vibegraph.auth.dto.AdminFeedbackResponse;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminReportController")
class AdminReportControllerTest {

    private MockMvc mockMvc;
    private AdminService adminService;
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        adminService = Mockito.mock(AdminService.class);
        currentUser = Mockito.mock(CurrentUser.class);
        AdminReportController controller = new AdminReportController(adminService, currentUser);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/reports lists all reports")
    void getReports_succeeds() throws Exception {
        AdminFeedbackResponse report = new AdminFeedbackResponse(
                UUID.randomUUID(), UUID.randomUUID(), "OPEN", "BUG", "UI error",
                Instant.now(), null, null);

        when(adminService.getFeedbackReports()).thenReturn(Collections.singletonList(report));

        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("UI error"));
    }

    @Test
    @DisplayName("GET /api/admin/reports/{reportId} returns details")
    void getReportDetail_succeeds() throws Exception {
        UUID reportId = UUID.randomUUID();
        AdminFeedbackResponse report = new AdminFeedbackResponse(
                reportId, UUID.randomUUID(), "OPEN", "BUG", "UI error",
                Instant.now(), null, null);
        AdminFeedbackDetailResponse detail = new AdminFeedbackDetailResponse(report, Collections.emptyList());

        when(adminService.getFeedbackReportDetail(reportId)).thenReturn(detail);

        mockMvc.perform(get("/api/admin/reports/" + reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.report.title").value("UI error"));
    }

    @Test
    @DisplayName("POST /api/admin/reports/{reportId}/reply adds reply")
    void reply_succeeds() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(currentUser.id()).thenReturn(adminId);

        mockMvc.perform(post("/api/admin/reports/" + reportId + "/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"We are checking this issue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminService).replyToFeedbackReport(any(UUID.class), any(UUID.class), any(AdminFeedbackReplyRequest.class));
    }

    @Test
    @DisplayName("PATCH /api/admin/reports/{reportId}/close closes report")
    void close_succeeds() throws Exception {
        UUID reportId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/reports/" + reportId + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminService).closeFeedbackReport(reportId);
    }
}
