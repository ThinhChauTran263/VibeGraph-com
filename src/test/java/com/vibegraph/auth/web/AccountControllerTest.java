package com.vibegraph.auth.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AccountProjectResponse;
import com.vibegraph.auth.dto.AccountProjectsPageResponse;
import com.vibegraph.auth.dto.AccountSessionStateResponse;
import com.vibegraph.auth.dto.AccountUsageResponse;
import com.vibegraph.auth.dto.FeatureCapability;
import com.vibegraph.auth.dto.AccountCreditLedgerResponse;
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
                .thenReturn(new UserResponse("user-1", "me@test.local", "Me", "USER", "ACTIVE", null));

        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("user-1"))
                .andExpect(jsonPath("$.data.email").value("me@test.local"))
                .andExpect(jsonPath("$.data.displayName").value("Me"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("GET /api/account/session-state returns the safe current account state")
    void sessionState_returnsSafeCurrentAccountState() throws Exception {
        when(accountService.sessionState()).thenReturn(new AccountSessionStateResponse(
                "user-1",
                "blocked@test.local",
                "Blocked User",
                "USER",
                "BLOCKED",
                "Policy review",
                java.util.Map.of(
                        "import.local", FeatureCapability.deny("Policy review"),
                        "registration", FeatureCapability.allow())));

        mockMvc.perform(get("/api/account/session-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("user-1"))
                .andExpect(jsonPath("$.data.email").value("blocked@test.local"))
                .andExpect(jsonPath("$.data.displayName").value("Blocked User"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.accountStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.data.safeReason").value("Policy review"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.blockedReason").doesNotExist())
                .andExpect(jsonPath("$.data.deactivationReason").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /api/account/profile updates displayName")
    void updateProfile_updatesDisplayName() throws Exception {
        when(accountService.updateProfile(any()))
                .thenReturn(new UserResponse("user-1", "me@test.local", "New Name", "USER", "ACTIVE", null));

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
    @DisplayName("PATCH /api/account/password changes password")
    void changePassword_succeeds() throws Exception {
        mockMvc.perform(patch("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"old-password","newPassword":"new-password","confirmPassword":"new-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(accountService).changePassword(any());
    }

    @Test
    @DisplayName("GET /api/account/usage returns quota snapshot")
    void usage_returnsQuotaSnapshot() throws Exception {
        when(accountService.usage())
                .thenReturn(new AccountUsageResponse(128L, 512L, 384L, "FREE", "Free", null, 25, 100, 75));

        mockMvc.perform(get("/api/account/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usedMb").value(128))
                .andExpect(jsonPath("$.data.limitMb").value(512))
                .andExpect(jsonPath("$.data.remainingMb").value(384))
                .andExpect(jsonPath("$.data.planCode").value("FREE"))
                .andExpect(jsonPath("$.data.planName").value("Free"))
                .andExpect(jsonPath("$.data.creditsUsed").value(25))
                .andExpect(jsonPath("$.data.creditsLimit").value(100))
                .andExpect(jsonPath("$.data.creditsRemaining").value(75));
    }

    @Test
    @DisplayName("GET /api/account/usage/ledger returns safe recent credit entries")
    void creditLedger_returnsRecentEntries() throws Exception {
        UUID ledgerId = UUID.randomUUID();
        when(accountService.creditLedger(10))
                .thenReturn(List.of(new AccountCreditLedgerResponse(
                        ledgerId,
                        "CLI",
                        "CLI_PUSH",
                        -2,
                        "project-1",
                        Instant.parse("2026-07-14T12:00:00Z"))));

        mockMvc.perform(get("/api/account/usage/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(ledgerId.toString()))
                .andExpect(jsonPath("$.data[0].source").value("CLI"))
                .andExpect(jsonPath("$.data[0].operationCode").value("CLI_PUSH"))
                .andExpect(jsonPath("$.data[0].creditsDelta").value(-2))
                .andExpect(jsonPath("$.data[0].projectId").value("project-1"));
    }

    @Test
    @DisplayName("GET /api/account/usage/ledger rejects oversized limit")
    void creditLedger_oversizedLimit_returnsValidationError() throws Exception {
        mockMvc.perform(get("/api/account/usage/ledger?limit=51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
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
