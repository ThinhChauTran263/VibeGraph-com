package com.vibegraph.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.entity.Announcement;
import com.vibegraph.auth.dto.AnnouncementRequest;
import com.vibegraph.auth.repository.AnnouncementRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.CurrentUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAnnouncementService")
class AdminAnnouncementServiceTest {

    @Mock private AnnouncementRepository announcementRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;
    @Mock private AuditService auditService;

    @Test
    @DisplayName("create strips markup so announcements are plain text")
    void create_htmlInput_stripsMarkup() {
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUser.id()).thenReturn(java.util.UUID.randomUUID());
        AdminAnnouncementService service = new AdminAnnouncementService(
                announcementRepository, userRepository, currentUser, auditService);

        var response = service.create(new AnnouncementRequest(
                "SECURITY",
                "WARNING",
                "ALL",
                "<b>Notice</b>",
                "<script>alert(1)</script>Rotate keys",
                null,
                null,
                true,
                true));

        assertEquals("Notice", response.title());
        assertEquals("alert(1)Rotate keys", response.body());
    }

    @Test
    @DisplayName("create returns safe creator display and email projection")
    void create_returnsCreatorProjection() {
        java.util.UUID creatorId = java.util.UUID.randomUUID();
        when(currentUser.id()).thenReturn(creatorId);
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(invocation -> {
            Announcement announcement = invocation.getArgument(0);
            announcement.setId(java.util.UUID.randomUUID());
            return announcement;
        });
        when(userRepository.findById(creatorId)).thenReturn(java.util.Optional.of(
                com.vibegraph.auth.domain.entity.User.builder()
                        .id(creatorId).displayName("Ops Admin").email("ops@test.local").build()));
        AdminAnnouncementService service = new AdminAnnouncementService(
                announcementRepository, userRepository, currentUser, auditService);

        var response = service.create(new AnnouncementRequest(
                "GENERAL", "INFO", "ALL", "Notice", "Body", null, null, true, true));

        assertEquals("Ops Admin", response.creatorDisplayName());
        assertEquals("ops@test.local", response.creatorEmail());
        verify(auditService).recordCurrentUser(
                "ANNOUNCEMENT_CREATE", null, "ANNOUNCEMENT", response.id().toString(),
                java.util.Map.of("type", "GENERAL", "severity", "INFO"));
    }
}
