package com.vibegraph.auth.web;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AccountProjectResponse;
import com.vibegraph.auth.dto.AccountProjectsPageResponse;
import com.vibegraph.auth.dto.AccountUsageResponse;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.service.AccountService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AccountController")
class AccountControllerTest {

    private MockMvc mockMvc;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = Mockito.mock(AccountService.class);
        AccountController controller = new AccountController(accountService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/account/profile returns current user profile")
    void profile_returnsCurrentUserProfile() throws Exception {
        when(accountService.profile())
                .thenReturn(new UserResponse("user-1", "me@test.local", "Me", "USER"));

        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("user-1"))
                .andExpect(jsonPath("$.data.email").value("me@test.local"))
                .andExpect(jsonPath("$.data.displayName").value("Me"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("PATCH /api/account/profile updates displayName")
    void updateProfile_updatesDisplayName() throws Exception {
        when(accountService.updateProfile(any()))
                .thenReturn(new UserResponse("user-1", "me@test.local", "New Name", "USER"));

        mockMvc.perform(patch("/api/account/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayName").value("New Name"))
                .andExpect(jsonPath("$.data.email").value("me@test.local"));

        verify(accountService).updateProfile(any());
    }

    @Test
    @DisplayName("PATCH /api/account/profile rejects blank displayName")
    void updateProfile_blankDisplayName_returnsValidationError() throws Exception {
        mockMvc.perform(patch("/api/account/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/account/usage returns quota snapshot")
    void usage_returnsQuotaSnapshot() throws Exception {
        when(accountService.usage())
                .thenReturn(new AccountUsageResponse(128L, 512L, 384L, "FREE", "Free", null));

        mockMvc.perform(get("/api/account/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usedBytes").value(128))
                .andExpect(jsonPath("$.data.limitBytes").value(512))
                .andExpect(jsonPath("$.data.remainingBytes").value(384))
                .andExpect(jsonPath("$.data.planCode").value("FREE"))
                .andExpect(jsonPath("$.data.planName").value("Free"));
    }

    @Test
    @DisplayName("GET /api/account/projects returns owner-scoped projects")
    void projects_returnsOwnerScopedProjects() throws Exception {
        AccountProjectResponse project = new AccountProjectResponse(
                "p1",
                "Project One",
                "LOCAL",
                1024L,
                "ANALYZED",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));
        when(accountService.projects(any())).thenReturn(new AccountProjectsPageResponse(
                java.util.List.of(project), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/account/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("p1"))
                .andExpect(jsonPath("$.data.items[0].name").value("Project One"))
                .andExpect(jsonPath("$.data.items[0].sourceType").value("LOCAL"))
                .andExpect(jsonPath("$.data.items[0].sizeBytes").value(1024))
                .andExpect(jsonPath("$.data.items[0].status").value("ANALYZED"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/account/projects rejects oversized page size")
    void projects_oversizedPageSize_returnsValidationError() throws Exception {
        mockMvc.perform(get("/api/account/projects?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
