package com.vibegraph.patch.controller;

import java.util.List;
import java.util.UUID;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountSettingsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.UnauthorizedException;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.patch.dto.request.PatchRequest;
import com.vibegraph.patch.dto.response.PatchResult;
import com.vibegraph.patch.exception.PatchExceptionHandler;
import com.vibegraph.patch.exception.PatchRejectedException;
import com.vibegraph.patch.exception.PatchRejectedException.Reason;
import com.vibegraph.patch.service.LocalPatchService;

/**
 * Web-layer tests for {@link LocalPatchController} using standalone MockMvc — no Neo4j, no full
 * Spring context. Verifies ownership gating (401/403), the success envelope, and that a
 * {@link PatchRejectedException} surfaces as {@code 400 PATCH_REJECTED}.
 *
 * Run: mvn test -Dtest=LocalPatchControllerTest
 */
@DisplayName("LocalPatchController")
class LocalPatchControllerTest {

    private static final String BODY = "{\"files\":[],\"deletions\":[],\"dryRun\":false}";

    private MockMvc mockMvc;
    private LocalPatchService localPatchService;
    private ProjectOwnershipGuard ownershipGuard;
    private AccountSettingsService accountSettingsService;
    private CurrentUser currentUser;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        localPatchService = Mockito.mock(LocalPatchService.class);
        ownershipGuard = Mockito.mock(ProjectOwnershipGuard.class);
        accountSettingsService = Mockito.mock(AccountSettingsService.class);
        currentUser = Mockito.mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(userId);
        LocalPatchController controller = new LocalPatchController(localPatchService, ownershipGuard, accountSettingsService, currentUser);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new PatchExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("returns 401 for an unauthenticated request and never touches the service")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        doThrow(new UnauthorizedException("No authenticated user"))
                .when(ownershipGuard).assertOwner("p1");

        mockMvc.perform(post("/api/projects/p1/patch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(localPatchService, never()).applyPatch(any(), any());
    }

    @Test
    @DisplayName("returns 403 for a non-owner and never touches the service")
    void shouldReturn403WhenNotOwner() throws Exception {
        doThrow(new ForbiddenException("Access denied")).when(ownershipGuard).assertOwner("p1");

        mockMvc.perform(post("/api/projects/p1/patch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(localPatchService, never()).applyPatch(any(), any());
    }

    @Test
    @DisplayName("returns 200 with the patch result for an owned project")
    void shouldApplyPatchForOwner() throws Exception {
        when(localPatchService.applyPatch(eq("p1"), any(PatchRequest.class)))
                .thenReturn(new PatchResult("p1", 1, 1, List.of(), true));

        mockMvc.perform(post("/api/projects/p1/patch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.changed").value(1))
                .andExpect(jsonPath("$.data.deleted").value(1))
                .andExpect(jsonPath("$.data.requiresAnalyze").value(true));

        verify(ownershipGuard).assertOwner("p1");
    }

    @Test
    @DisplayName("maps a rejected patch to 400 PATCH_REJECTED without leaking content")
    void shouldReturn400WhenRejected() throws Exception {
        when(localPatchService.applyPatch(eq("p1"), any(PatchRequest.class)))
                .thenThrow(new PatchRejectedException(Reason.PATH_TRAVERSAL, "path must not contain '..'"));

        mockMvc.perform(post("/api/projects/p1/patch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PATCH_REJECTED"));
    }
}
