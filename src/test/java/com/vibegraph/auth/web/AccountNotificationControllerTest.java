package com.vibegraph.auth.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.auth.dto.NotificationResponse;
import com.vibegraph.auth.service.NotificationService;
import com.vibegraph.common.exception.GlobalExceptionHandler;

@DisplayName("Account notification API")
class AccountNotificationControllerTest {

    private MockMvc mockMvc;
    private NotificationService notificationService;
    private UUID notificationId;
    private NotificationResponse notification;

    @BeforeEach
    void setUp() {
        notificationService = Mockito.mock(NotificationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountNotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        notificationId = UUID.randomUUID();
        notification = new NotificationResponse(
                notificationId, UUID.randomUUID(), "Maintenance", "Restart at 22:00", "Ops",
                "Ops", "ops@test.local", Instant.parse("2026-07-16T10:00:00Z"),
                "WARNING", "MAINTENANCE", true, false, null, null);
    }

    @Test
    @DisplayName("list returns newest persisted notification projection")
    void list_returnsSafeProjection() throws Exception {
        when(notificationService.list(50)).thenReturn(List.of(notification));

        mockMvc.perform(get("/api/account/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.data[0].creatorDisplayName").value("Ops"))
                .andExpect(jsonPath("$.data[0].creatorEmail").value("ops@test.local"))
                .andExpect(jsonPath("$.data[0].severity").value("WARNING"));
    }

    @Test
    @DisplayName("detail, mark read, and dismiss use owner-scoped service operations")
    void stateEndpoints_returnPersistedState() throws Exception {
        NotificationResponse read = new NotificationResponse(
                notification.id(), notification.announcementId(), notification.title(), notification.body(),
                notification.creatorName(), notification.creatorDisplayName(), notification.creatorEmail(),
                notification.createdAt(), notification.severity(), notification.type(), true, true,
                Instant.parse("2026-07-16T10:05:00Z"), null);
        NotificationResponse dismissed = new NotificationResponse(
                read.id(), read.announcementId(), read.title(), read.body(), read.creatorName(),
                read.creatorDisplayName(), read.creatorEmail(), read.createdAt(), read.severity(), read.type(),
                true, true, read.readAt(), Instant.parse("2026-07-16T10:06:00Z"));
        when(notificationService.get(notificationId)).thenReturn(notification);
        when(notificationService.markRead(notificationId)).thenReturn(read);
        when(notificationService.dismiss(notificationId)).thenReturn(dismissed);

        mockMvc.perform(get("/api/account/notifications/{id}", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Maintenance"));
        mockMvc.perform(patch("/api/account/notifications/{id}/read", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(patch("/api/account/notifications/{id}/dismiss", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dismissedAt").exists());
    }
}
