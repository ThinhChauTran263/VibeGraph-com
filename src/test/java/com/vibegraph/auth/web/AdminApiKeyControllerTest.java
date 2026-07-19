package com.vibegraph.auth.web;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.ApiKeyResponse;
import com.vibegraph.auth.service.ApiKeyService;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.GlobalExceptionHandler;

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
    @DisplayName("PATCH /api/admin/api-keys/{id}/lock locks a key")
    void lock_asAdmin_succeeds() throws Exception {
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/api-keys/" + keyId + "/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(apiKeyService).disableForAnyUser(keyId);
    }

    @Test
    @DisplayName("PATCH /api/admin/api-keys/{id}/unlock resolves an administrator lock")
    void unlock_asAdmin_succeeds() throws Exception {
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/api-keys/" + keyId + "/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(apiKeyService).unlockForAnyUser(keyId);
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
