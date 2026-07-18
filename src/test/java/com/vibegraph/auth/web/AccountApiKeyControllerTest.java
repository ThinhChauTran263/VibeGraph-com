package com.vibegraph.auth.web;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.ApiKeyCreateRequest;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.dto.ApiKeyResponse;
import com.vibegraph.auth.service.ApiKeyService;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.ApiKeyAdminLockedException;
import com.vibegraph.common.exception.ApiKeyPlanLimitReachedException;
import com.vibegraph.common.exception.ApiKeysDisabledException;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.GlobalExceptionHandler;

@DisplayName("AccountApiKeyController")
class AccountApiKeyControllerTest {

    private MockMvc mockMvc;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = Mockito.mock(ApiKeyService.class);
        AccountApiKeyController controller = new AccountApiKeyController(apiKeyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/account/api-keys creates key with secret")
    void create_returnsSecretKey() throws Exception {
        ApiKeyCreateResponse response = new ApiKeyCreateResponse(
                UUID.randomUUID(),
                "vbg_test1234",
                "Test Key",
                "vbg_abcdefgh12345678901234567890ab",
                new com.vibegraph.auth.dto.ProjectBindingResponse(
                        "project-1", "Project One", "LOCAL", "ANALYZED"),
                Instant.now(),
                null);
        when(apiKeyService.createForCurrentUser(any(ApiKeyCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/account/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Key\",\"projectId\":\"project-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.secretKey").value("vbg_abcdefgh12345678901234567890ab"))
                .andExpect(jsonPath("$.data.keyPrefix").value("vbg_test1234"))
                .andExpect(jsonPath("$.data.name").value("Test Key"))
                .andExpect(jsonPath("$.data.project.id").value("project-1"))
                .andExpect(jsonPath("$.data.project.name").value("Project One"));

        verify(apiKeyService).createForCurrentUser(any());
    }

    @Test
    @DisplayName("POST /api/account/api-keys rejects missing projectId")
    void create_missingProjectId_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/account/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Key\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/account/api-keys rejects blank name")
    void create_blankName_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/account/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"projectId\":\"project-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/account/api-keys returns 403 when blocked")
    void create_blockedUser_returnsForbidden() throws Exception {
        when(apiKeyService.createForCurrentUser(any(ApiKeyCreateRequest.class)))
                .thenThrow(new AccountBlockedException("Blocked", "Account is blocked"));

        mockMvc.perform(post("/api/account/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Key\",\"projectId\":\"project-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_BLOCKED"));
    }

    @Test
    @DisplayName("POST /api/account/api-keys returns 403 when creation disabled")
    void create_creationDisabled_returnsForbidden() throws Exception {
        when(apiKeyService.createForCurrentUser(any(ApiKeyCreateRequest.class)))
                .thenThrow(new ApiKeysDisabledException("API key creation is disabled"));

        mockMvc.perform(post("/api/account/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Key\",\"projectId\":\"project-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("API_KEYS_DISABLED"));
    }

    @Test
    @DisplayName("POST /api/account/api-keys returns 409 when plan limit reached")
    void create_planLimitReached_returnsConflict() throws Exception {
        when(apiKeyService.createForCurrentUser(any(ApiKeyCreateRequest.class)))
                .thenThrow(new ApiKeyPlanLimitReachedException("API key limit reached: 3/3"));

        mockMvc.perform(post("/api/account/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Key\",\"projectId\":\"project-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("API_KEY_PLAN_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("GET /api/account/api-keys lists keys without secrets")
    void list_returnsKeysWithoutSecrets() throws Exception {
        ApiKeyResponse key1 = new ApiKeyResponse(
                UUID.randomUUID(),
                "vbg_test1234",
                "Key 1",
                Instant.now(),
                null,
                null,
                null);
        ApiKeyResponse key2 = new ApiKeyResponse(
                UUID.randomUUID(),
                "vbg_abcd5678",
                "Key 2",
                Instant.now(),
                Instant.now(),
                null,
                null);
        when(apiKeyService.listForCurrentUser()).thenReturn(java.util.List.of(key1, key2));

        mockMvc.perform(get("/api/account/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].keyPrefix").value("vbg_test1234"))
                .andExpect(jsonPath("$.data[0].name").value("Key 1"))
                .andExpect(jsonPath("$.data[0].secretKey").doesNotExist())
                .andExpect(jsonPath("$.data[1].keyPrefix").value("vbg_abcd5678"))
                .andExpect(jsonPath("$.data[1].name").value("Key 2"));
    }

    @Test
    @DisplayName("PATCH /api/account/api-keys/{id}/disable disables owned key")
    void disable_ownedKey_succeeds() throws Exception {
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(patch("/api/account/api-keys/" + keyId + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(apiKeyService).disableForCurrentUser(keyId);
    }

    @Test
    @DisplayName("PATCH /api/account/api-keys/{id}/disable returns 403 for non-owned key")
    void disable_nonOwnedKey_returnsForbidden() throws Exception {
        UUID keyId = UUID.randomUUID();
        Mockito.doThrow(new ForbiddenException("Access denied"))
                .when(apiKeyService).disableForCurrentUser(keyId);

        mockMvc.perform(patch("/api/account/api-keys/" + keyId + "/disable"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("DELETE /api/account/api-keys/{id} deletes owned key")
    void delete_ownedKey_succeeds() throws Exception {
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(delete("/api/account/api-keys/" + keyId))
                .andExpect(status().isNoContent());

        verify(apiKeyService).deleteForCurrentUser(keyId);
    }

    @Test
    @DisplayName("DELETE /api/account/api-keys/{id} returns 403 for admin locked key")
    void delete_adminLockedKey_returnsForbidden() throws Exception {
        UUID keyId = UUID.randomUUID();
        Mockito.doThrow(new ApiKeyAdminLockedException("Administrator-locked API keys cannot be deleted"))
                .when(apiKeyService).deleteForCurrentUser(keyId);

        mockMvc.perform(delete("/api/account/api-keys/" + keyId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("API_KEY_ADMIN_LOCKED"));
    }
}
