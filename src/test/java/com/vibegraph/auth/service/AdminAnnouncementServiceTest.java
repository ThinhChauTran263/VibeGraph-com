package com.vibegraph.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.Announcement;
import com.vibegraph.auth.dto.AnnouncementRequest;
import com.vibegraph.auth.repository.AnnouncementRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAnnouncementService")
class AdminAnnouncementServiceTest {

    @Mock private AnnouncementRepository announcementRepository;

    @Test
    @DisplayName("create strips markup so announcements are plain text")
    void create_htmlInput_stripsMarkup() {
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminAnnouncementService service = new AdminAnnouncementService(announcementRepository);

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
}
