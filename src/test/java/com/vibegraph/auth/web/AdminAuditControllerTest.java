package com.vibegraph.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AuditLogResponse;
import com.vibegraph.auth.dto.AuditRetentionResponse;
import com.vibegraph.auth.service.AuditLogEventStream;
import com.vibegraph.auth.service.AuditService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

@DisplayName("Admin audit API")
class AdminAuditControllerTest {

    private MockMvc mockMvc;
    private AuditService auditService;
    private AuditLogEventStream auditLogEventStream;

    @BeforeEach
    void setUp() {
        auditService = Mockito.mock(AuditService.class);
        auditLogEventStream = Mockito.mock(AuditLogEventStream.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAuditController(auditService, auditLogEventStream))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("stream opens an SSE response")
    void stream_succeeds() throws Exception {
        when(auditLogEventStream.subscribe()).thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());

        mockMvc.perform(get("/api/admin/audit-logs/stream"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted());
    }

    @Test
    @DisplayName("list and detail return redacted audit projections")
    void listAndDetail_succeed() throws Exception {
        UUID id = UUID.randomUUID();
        AuditLogResponse response = new AuditLogResponse(
                id, "FAILED_LOGIN", null, null, "USER", "user@test.local", "FAILURE",
                "127.0.0.1", "{\"password\":\"[REDACTED]\"}", Instant.now());
        when(auditService.list(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response)));
        when(auditService.get(id)).thenReturn(response);

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("FAILED_LOGIN"))
                .andExpect(jsonPath("$.data.content[0].details").value("{\"password\":\"[REDACTED]\"}"));
        mockMvc.perform(get("/api/admin/audit-logs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    @DisplayName("retention defaults and updates through admin API")
    void retention_succeeds() throws Exception {
        when(auditService.getRetention()).thenReturn(new AuditRetentionResponse(90, null, null));
        when(auditService.updateRetention(120)).thenReturn(new AuditRetentionResponse(120, UUID.randomUUID(), null));

        mockMvc.perform(get("/api/admin/audit-logs/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retentionDays").value(90));
        mockMvc.perform(put("/api/admin/audit-logs/retention")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retentionDays").value(120));
    }
}
