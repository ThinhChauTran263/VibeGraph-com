package com.vibegraph.auth.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.Announcement;
import com.vibegraph.auth.dto.AnnouncementRequest;
import com.vibegraph.auth.dto.AnnouncementResponse;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.repository.AnnouncementRepository;
import com.vibegraph.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public List<AnnouncementResponse> list() {
        return announcementRepository.findAll().stream()
                .sorted(Comparator.comparing(Announcement::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(transactionManager = "supabaseTransactionManager")
    public AnnouncementResponse create(AnnouncementRequest request) {
        Announcement announcement = toAnnouncement(Announcement.builder().build(), request);
        announcement.setCreatedByUserId(currentUser.id());
        announcement.setCreatedAt(java.time.Instant.now());
        AnnouncementResponse response = toResponse(announcementRepository.save(announcement));
        auditService.recordCurrentUser("ANNOUNCEMENT_CREATE", null, "ANNOUNCEMENT",
                response.id() == null ? null : response.id().toString(),
                java.util.Map.of("type", response.type(), "severity", response.severity()));
        return response;
    }

    @Transactional(transactionManager = "supabaseTransactionManager")
    public AnnouncementResponse update(UUID id, AnnouncementRequest request) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + id));
        AnnouncementResponse response = toResponse(announcementRepository.save(toAnnouncement(announcement, request)));
        auditService.recordCurrentUser("ANNOUNCEMENT_UPDATE", null, "ANNOUNCEMENT", id.toString(),
                java.util.Map.of("type", response.type(), "severity", response.severity()));
        return response;
    }

    @Transactional(transactionManager = "supabaseTransactionManager")
    public void delete(UUID id) {
        announcementRepository.deleteById(id);
        auditService.recordCurrentUser("ANNOUNCEMENT_DELETE", null, "ANNOUNCEMENT", id.toString(), java.util.Map.of());
    }

    @Transactional(transactionManager = "supabaseTransactionManager")
    public AnnouncementResponse disable(UUID id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + id));
        announcement.setActive(false);
        AnnouncementResponse response = toResponse(announcementRepository.save(announcement));
        auditService.recordCurrentUser("ANNOUNCEMENT_DISABLE", null, "ANNOUNCEMENT", id.toString(), java.util.Map.of());
        return response;
    }

    private Announcement toAnnouncement(Announcement announcement, AnnouncementRequest request) {
        if (request.startsAt() != null && request.endsAt() != null
                && !request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("Announcement end time must be after start time");
        }
        announcement.setType(request.type());
        announcement.setSeverity(request.severity());
        announcement.setTarget(request.target());
        announcement.setTitle(toPlainText(request.title()));
        announcement.setBody(toPlainText(request.body()));
        announcement.setStartsAt(request.startsAt());
        announcement.setEndsAt(request.endsAt());
        announcement.setDismissible(request.dismissible());
        announcement.setActive(request.active());
        return announcement;
    }

    private String toPlainText(String value) {
        return value.replaceAll("<[^>]*>", "").trim();
    }

    private AnnouncementResponse toResponse(Announcement announcement) {
        AnnouncementResponse base = AnnouncementResponse.from(announcement);
        if (announcement.getCreatedByUserId() == null) {
            return base;
        }
        return userRepository.findById(announcement.getCreatedByUserId())
                .map(user -> new AnnouncementResponse(
                        base.id(), base.type(), base.severity(), base.target(), base.title(), base.body(),
                        base.startsAt(), base.endsAt(), base.dismissible(), base.active(), base.createdByUserId(),
                        user.getDisplayName(), user.getEmail(), base.createdAt()))
                .orElse(base);
    }
}
