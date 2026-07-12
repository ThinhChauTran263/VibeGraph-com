package com.vibegraph.auth.web;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.AdminCreateUserRequest;
import com.vibegraph.auth.dto.AdminUserBlockRequest;
import com.vibegraph.auth.dto.AdminUserResponse;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminUserController")
class AdminUserControllerTest {

    private MockMvc mockMvc;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = Mockito.mock(AdminService.class);
        AdminUserController controller = new AdminUserController(adminService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/users lists all users")
    void list_succeeds() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserResponse userResponse = new AdminUserResponse(
                userId, "test@test.local", "Test User", "USER",
                false, false, null, null, "FREE",
                null, null, 100L, 0L);

        when(adminService.getUsers(any(), any())).thenReturn(new PageImpl<>(Collections.singletonList(userResponse)));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].email").value("test@test.local"));
    }

    @Test
    @DisplayName("POST /api/admin/users creates a user")
    void create_succeeds() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserResponse userResponse = new AdminUserResponse(
                userId, "new@test.local", "New User", "USER",
                false, false, null, null, "FREE",
                null, null, 100L, 0L);

        when(adminService.createUser(any(AdminCreateUserRequest.class))).thenReturn(userResponse);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@test.local\",\"displayName\":\"New User\",\"role\":\"USER\",\"planCode\":\"FREE\",\"temporaryPassword\":\"password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new@test.local"));
    }

    @Test
    @DisplayName("PATCH /api/admin/users/{userId}/block blocks a user")
    void block_succeeds() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserResponse userResponse = new AdminUserResponse(
                userId, "blocked@test.local", "Blocked", "USER",
                false, true, "Spam", "Spam Policy", "FREE",
                null, null, 100L, 0L);

        when(adminService.blockUser(any(UUID.class), any(AdminUserBlockRequest.class))).thenReturn(userResponse);

        mockMvc.perform(patch("/api/admin/users/" + userId + "/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Spam\",\"safeReason\":\"Spam Policy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.blocked").value(true));
    }
}
