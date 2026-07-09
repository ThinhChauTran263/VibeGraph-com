package com.vibegraph.auth.web;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AdminApiKeyCreateRequest;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.dto.ApiKeyResponse;
import com.vibegraph.auth.service.ApiKeyService;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminApiKeyController")
class AdminApiKeyControllerTest {

    private MockMvc mockMvc;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = Mockito.mock(ApiKeyService.class);
        AdminApiKeyController controller = new AdminApiKeyController(apiKeyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/admin/api-keys creates key for specified user")
    void create_asAdmin_succeeds() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        ApiKeyCreateResponse response = new ApiKeyCreateResponse(
                UUID.randomUUID(),
                "vbg_test1234",
                "Admin Created Key",
                "vbg_abcdefgh12345678901234567890ab",
                Instant.now(),
                null);
        when(apiKeyService.createForUser(any(AdminApiKeyCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + targetUserId + "\",\"name\":\"Admin Created Key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.secretKey").value("vbg_abcdefgh12345678901234567890ab"))
                .andExpect(jsonPath("$.data.name").value("Admin Created Key"));

        verify(apiKeyService).createForUser(any());
    }

    @Test
    @DisplayName("POST /api/admin/api-keys requires userId")
    void create_missingUserId_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Key\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/admin/api-keys returns 403 when non-admin")
    void create_asNonAdmin_returnsForbidden() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        when(apiKeyService.createForUser(any(AdminApiKeyCreateRequest.class)))
                .thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(post("/api/admin/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + targetUserId + "\",\"name\":\"Test Key\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/admin/api-keys lists keys for specified user")
    void list_asAdmin_succeeds() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        ApiKeyResponse key = new ApiKeyResponse(
                UUID.randomUUID(),
                "vbg_test1234",
                "User Key",
                Instant.now(),
                null,
                null,
                null);
        when(apiKeyService.listForUser(targetUserId)).thenReturn(java.util.List.of(key));

        mockMvc.perform(get("/api/admin/api-keys?userId=" + targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("User Key"));

        verify(apiKeyService).listForUser(targetUserId);
    }

    @Test
    @DisplayName("GET /api/admin/api-keys returns 403 when non-admin")
    void list_asNonAdmin_returnsForbidden() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        when(apiKeyService.listForUser(targetUserId))
                .thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(get("/api/admin/api-keys?userId=" + targetUserId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /api/admin/api-keys/{id}/disable disables any user's key")
    void disable_asAdmin_succeeds() throws Exception {
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/api-keys/" + keyId + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(apiKeyService).disableForAnyUser(keyId);
    }

    @Test
    @DisplayName("PATCH /api/admin/api-keys/{id}/disable returns 403 when non-admin")
    void disable_asNonAdmin_returnsForbidden() throws Exception {
        UUID keyId = UUID.randomUUID();
        Mockito.doThrow(new ForbiddenException("Access denied"))
                .when(apiKeyService).disableForAnyUser(keyId);

        mockMvc.perform(patch("/api/admin/api-keys/" + keyId + "/disable"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
